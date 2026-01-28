package pt.raidline.vessel.async;

import pt.raidline.vessel.Failure;
import pt.raidline.vessel.Success;
import pt.raidline.vessel.Vessel;
import pt.raidline.vessel.exception.AsyncUnwrapException;
import pt.raidline.vessel.exception.ValueNotPresentException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An async variant of {@link Vessel} that wraps asynchronous computations.
 * <p>
 * AsyncVessel can be in one of two states:
 * <ul>
 *   <li>{@link AsyncPending} - Wraps a pending or completed CompletionStage</li>
 *   <li>{@link AsyncFailure} - The computation has already failed with an exception</li>
 * </ul>
 * <p>
 * When the computation completes, {@link #onComplete(Consumer)} will receive
 * either a {@link Success} or {@link Failure} from the synchronous Vessel API.
 *
 * @param <V> the value type
 * @param <E> the error type (must extend Exception)
 */
public sealed interface AsyncVessel<V, E extends Exception> permits AsyncPending, AsyncFailure {

    // ==================== STATIC FACTORY METHODS ====================

    /**
     * Lifts an async computation into an AsyncVessel.
     * <p>
     * The supplier is invoked immediately and the resulting CompletionStage
     * is wrapped in an {@link AsyncPending}.
     *
     * @param async the supplier of the async computation
     * @param <V>   the value type
     * @param <E>   the error type
     * @return an AsyncVessel wrapping the async computation
     * @throws NullPointerException if async is null or returns null
     */
    static <V, E extends Exception> AsyncVessel<V, E> lift(Supplier<CompletionStage<V>> async) {
        Objects.requireNonNull(async, "async supplier cannot be null");

        CompletionStage<V> stage = async.get();
        Objects.requireNonNull(stage, "async supplier returned null CompletionStage");

        return new AsyncPending<>(stage);
    }

    /**
     * Creates an immediately successful AsyncVessel with the given value.
     *
     * @param value the success value
     * @param <V>   the value type
     * @param <E>   the error type
     * @return an AsyncVessel that is already completed with the value
     */
    static <V, E extends Exception> AsyncVessel<V, E> success(V value) {
        return new AsyncPending<>(CompletableFuture.completedFuture(value));
    }

    /**
     * Creates an immediately failed AsyncVessel with the given exception.
     *
     * @param exception the failure exception
     * @param <V>       the value type
     * @param <E>       the error type
     * @return an AsyncVessel that represents a failure
     * @throws NullPointerException if exception is null
     */
    static <V, E extends Exception> AsyncVessel<V, E> failure(E exception) {
        Objects.requireNonNull(exception, "exception cannot be null");
        return new AsyncFailure<>(exception);
    }

    /**
     * Creates an AsyncVessel from a CompletionStage.
     *
     * @param stage the completion stage
     * @param <V>   the value type
     * @param <E>   the error type
     * @return an AsyncVessel wrapping the stage
     * @throws NullPointerException if stage is null
     */
    static <V, E extends Exception> AsyncVessel<V, E> fromStage(CompletionStage<V> stage) {
        Objects.requireNonNull(stage, "stage cannot be null");
        return new AsyncPending<>(stage);
    }

    /**
     * Transforms the success value using the given mapper function.
     * <p>
     * If this is an {@link AsyncFailure}, returns an unchanged failure.
     * If this is an {@link AsyncPending}, the mapper is applied when the future completes.
     *
     * @param mapper the transformation function
     * @param <R>    the result type
     * @return a new AsyncVessel with the transformed value
     * @throws NullPointerException if mapper is null
     */
    default <R> AsyncVessel<R, E> mapAsync(Function<V, R> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");

        if (this instanceof AsyncPending<V, E>(var stage)) {
            return new AsyncPending<>(stage.thenApply(mapper));
        }

        // AsyncFailure - propagate unchanged
        @SuppressWarnings("unchecked")
        AsyncVessel<R, E> result = (AsyncVessel<R, E>) this;
        return result;
    }

    /**
     * Transforms the error using the given mapper function.
     * <p>
     * If this is an {@link AsyncPending} with a pending future, errors will be
     * transformed when they occur. If this is an {@link AsyncFailure}, the
     * error is transformed immediately.
     *
     * @param mapper the error transformation function
     * @param <T>    the new error type
     * @return a new AsyncVessel with the transformed error type
     * @throws NullPointerException if mapper is null
     */
    default <T extends Exception> AsyncVessel<V, T> mapErrorAsync(Function<E, T> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");

        if (this instanceof AsyncFailure<V, E>(var ex)) {
            return new AsyncFailure<>(mapper.apply(ex));
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            CompletableFuture<V> newFuture = new CompletableFuture<>();

            stage.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrapException(throwable);
                    @SuppressWarnings("unchecked")
                    E typedCause = (E) cause;
                    T mappedError = mapper.apply(typedCause);
                    newFuture.completeExceptionally(mappedError);
                } else {
                    newFuture.complete(value);
                }
            });

            return new AsyncPending<>(newFuture);
        }

        @SuppressWarnings("unchecked")
        AsyncVessel<V, T> result = (AsyncVessel<V, T>) this;
        return result;
    }

    /**
     * Chains another async operation that returns a CompletionStage.
     * <p>
     * If this is an {@link AsyncFailure}, returns an unchanged failure.
     *
     * @param mapper the function returning a CompletionStage
     * @param <R>    the result type
     * @return a new AsyncVessel with the chained computation
     * @throws NullPointerException if mapper is null
     */
    default <R> AsyncVessel<R, E> flatMapAsync(Function<? super V, ? extends CompletionStage<R>> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");

        if (this instanceof AsyncPending<V, E>(var stage)) {
            return new AsyncPending<>(stage.thenCompose(mapper));
        }

        // AsyncFailure - propagate unchanged
        @SuppressWarnings("unchecked")
        AsyncVessel<R, E> result = (AsyncVessel<R, E>) this;
        return result;
    }

    /**
     * Chains another async operation that returns an AsyncVessel.
     * <p>
     * If this is an {@link AsyncFailure}, returns an unchanged failure.
     *
     * @param mapper the function returning an AsyncVessel
     * @param <R>    the result type
     * @param <T>    the new error type
     * @return a new AsyncVessel with the chained computation
     * @throws NullPointerException if mapper is null
     */
    default <R, T extends Exception> AsyncVessel<R, T> flatMap(Function<? super V, ? extends AsyncVessel<R, T>> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");

        if (this instanceof AsyncPending<V, E>(var stage)) {
            CompletableFuture<R> resultFuture = stage.toCompletableFuture().thenCompose(value -> {
                AsyncVessel<R, T> mapped = mapper.apply(value);

                if (mapped instanceof AsyncPending<R, T>(var innerStage)) {
                    return innerStage.toCompletableFuture();
                }

                var casted = (AsyncFailure<R, T>) mapped;
                return CompletableFuture.failedFuture(casted.ex());
            });

            return new AsyncPending<>(resultFuture);
        }

        @SuppressWarnings("unchecked")
        AsyncVessel<R, T> result = (AsyncVessel<R, T>) this;
        return result;
    }

    /**
     * Recovers from a failure by providing an alternative value.
     * <p>
     * If this is successful, returns unchanged. If this is a failure,
     * applies the recovery function to produce a success value.
     *
     * @param recovery the recovery function
     * @return an AsyncVessel with the recovered value
     * @throws NullPointerException if recovery is null
     */
    default AsyncVessel<V, E> recover(Function<E, V> recovery) {
        Objects.requireNonNull(recovery, "recovery cannot be null");

        if (this instanceof AsyncFailure<V, E>(var ex)) {
            return new AsyncPending<>(CompletableFuture.completedFuture(recovery.apply(ex)));
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            CompletableFuture<V> recoveredFuture = new CompletableFuture<>();

            stage.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrapException(throwable);
                    try {
                        @SuppressWarnings("unchecked")
                        E typedCause = (E) cause;
                        V recovered = recovery.apply(typedCause);
                        recoveredFuture.complete(recovered);
                    } catch (Exception e) {
                        recoveredFuture.completeExceptionally(e);
                    }
                } else {
                    recoveredFuture.complete(value);
                }
            });

            return new AsyncPending<>(recoveredFuture);
        }

        return this;
    }

    /**
     * Recovers from a failure by providing an alternative AsyncVessel.
     * <p>
     * If this is successful, returns unchanged. If this is a failure,
     * applies the recovery function to produce a new AsyncVessel.
     *
     * @param recovery the recovery function
     * @return an AsyncVessel with the recovered computation
     * @throws NullPointerException if recovery is null
     */
    default AsyncVessel<V, E> recoverWith(Function<E, AsyncVessel<V, E>> recovery) {
        Objects.requireNonNull(recovery, "recovery cannot be null");

        if (this instanceof AsyncFailure<V, E>(var ex)) {
            return recovery.apply(ex);
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            CompletableFuture<V> recoveredFuture = new CompletableFuture<>();

            stage.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrapException(throwable);
                    @SuppressWarnings("unchecked")
                    E typedCause = (E) cause;
                    AsyncVessel<V, E> recovered = recovery.apply(typedCause);

                    if (recovered instanceof AsyncPending<V, E>(var innerStage)) {
                        innerStage.whenComplete((v, t) -> {
                            if (t != null) {
                                recoveredFuture.completeExceptionally(t);
                            } else {
                                recoveredFuture.complete(v);
                            }
                        });
                    } else if (recovered instanceof AsyncFailure<V, E>(var e)) {
                        recoveredFuture.completeExceptionally(e);
                    }
                } else {
                    recoveredFuture.complete(value);
                }
            });

            return new AsyncPending<>(recoveredFuture);
        }

        return this;
    }

    /**
     * Adds a timeout to the async operation.
     * <p>
     * If the operation does not complete within the specified duration,
     * it will fail with the error produced by the timeoutError supplier.
     *
     * @param timeout      the maximum duration to wait
     * @param timeoutError the supplier for the timeout exception
     * @return an AsyncVessel with timeout handling
     * @throws NullPointerException if timeout or timeoutError is null
     */
    default AsyncVessel<V, E> withTimeout(Duration timeout, Supplier<E> timeoutError) {
        Objects.requireNonNull(timeout, "timeout cannot be null");
        Objects.requireNonNull(timeoutError, "timeoutError cannot be null");

        if (this instanceof AsyncPending<V, E>(var stage)) {
            CompletableFuture<V> future = stage.toCompletableFuture();
            CompletableFuture<V> timeoutFuture = future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);

            CompletableFuture<V> handledFuture = timeoutFuture.exceptionally(th -> {
                if (th instanceof TimeoutException || th.getCause() instanceof TimeoutException) {
                    throw new CompletionException(timeoutError.get());
                }
                if (th instanceof CompletionException ce) {
                    throw ce;
                }
                throw new CompletionException(th);
            });

            return new AsyncPending<>(handledFuture);
        }

        return this;
    }

    /**
     * Blocks and unwraps the value from this AsyncVessel.
     * <p>
     * If this is an {@link AsyncFailure}, throws {@link ValueNotPresentException}.
     * If this is an {@link AsyncPending}, blocks until completion.
     *
     * @return the unwrapped value
     * @throws ValueNotPresentException if this is a failure
     * @throws AsyncUnwrapException     if blocking fails
     */
    default V unwrap() {
        if (this instanceof AsyncFailure<V, E>) {
            throw new ValueNotPresentException(
                    "Cannot unwrap AsyncFailure - the async operation has failed"
            );
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            try {
                return stage.toCompletableFuture().get();
            } catch (ExecutionException e) {
                throw new AsyncUnwrapException(
                        "Blocking future caught an exception: " + e.getCause().getMessage(), e.getCause()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AsyncUnwrapException("Blocking future was interrupted", e);
            }
        }

        throw new IllegalStateException("Unknown AsyncVessel type: " + this.getClass());
    }

    /**
     * Blocks and unwraps the value with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return the unwrapped value
     * @throws ValueNotPresentException if this is a failure
     * @throws AsyncUnwrapException     if blocking fails or times out
     */
    default V unwrap(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");

        if (this instanceof AsyncFailure<V, E>) {
            throw new ValueNotPresentException(
                    "Cannot unwrap AsyncFailure - the async operation has failed"
            );
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            try {
                return stage.toCompletableFuture().get(timeout, unit);
            } catch (TimeoutException e) {
                throw new AsyncUnwrapException("Blocking future timed out after " + timeout + " " + unit, e);
            } catch (ExecutionException e) {
                throw new AsyncUnwrapException("Blocking future caught an exception: " + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AsyncUnwrapException("Blocking future was interrupted", e);
            }
        }

        throw new IllegalStateException("Unknown AsyncVessel type: " + this.getClass());
    }

    /**
     * Registers a callback to be invoked when the async operation completes.
     * <p>
     * The consumer receives a synchronous {@link Vessel} - either {@link Success}
     * or {@link Failure} - when the async operation completes.
     * <p>
     * If this is already an {@link AsyncFailure}, the callback is invoked immediately.
     *
     * @param consumer the callback to invoke on completion
     * @throws NullPointerException if consumer is null
     */
    default void onComplete(Consumer<Vessel<V, E>> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");

        if (this instanceof AsyncFailure<V, E>(var ex)) {
            consumer.accept(new Failure<>(ex));
            return;
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            stage.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    Throwable cause = unwrapException(throwable);
                    @SuppressWarnings("unchecked")
                    E typedCause = (E) cause;
                    consumer.accept(new Failure<>(typedCause));
                } else {
                    consumer.accept(new Success<>(value));
                }
            });
        }
    }

    /**
     * Converts this AsyncVessel to a synchronous Vessel by blocking.
     * <p>
     * Use with caution as this blocks the current thread.
     *
     * @return the synchronous Vessel result
     */
    default Vessel<V, E> toVessel() {
        if (this instanceof AsyncFailure<V, E>(var ex)) {
            return new Failure<>(ex);
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            try {
                V value = stage.toCompletableFuture().get();
                return new Success<>(value);
            } catch (ExecutionException e) {
                Throwable cause = unwrapException(e);
                @SuppressWarnings("unchecked")
                E typedCause = (E) cause;
                return new Failure<>(typedCause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                @SuppressWarnings("unchecked")
                E typedCause = (E) new RuntimeException("Interrupted while waiting", e);
                return new Failure<>(typedCause);
            }
        }

        throw new IllegalStateException("Unknown AsyncVessel type: " + this.getClass());
    }

    /**
     * Converts this AsyncVessel to a synchronous Vessel with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return the synchronous Vessel result
     */
    default Vessel<V, E> toVessel(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit cannot be null");

        if (this instanceof AsyncFailure<V, E>(var ex)) {
            return new Failure<>(ex);
        }

        if (this instanceof AsyncPending<V, E>(var stage)) {
            try {
                V value = stage.toCompletableFuture().get(timeout, unit);
                return new Success<>(value);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Throwable cause = unwrapException(e);
                @SuppressWarnings("unchecked")
                E typedCause = (E) cause;
                return new Failure<>(typedCause);
            } catch (Exception e) {
                Throwable cause = unwrapException(e);
                @SuppressWarnings("unchecked")
                E typedCause = (E) cause;
                return new Failure<>(typedCause);
            }
        }

        throw new IllegalStateException("Unknown AsyncVessel type: " + this.getClass());
    }

    /**
     * Checks if this AsyncVessel is already known to be a failure.
     * <p>
     * Note: An {@link AsyncPending} may still complete exceptionally,
     * so this only checks for immediate failures.
     *
     * @return true if this is an AsyncFailure
     */
    default boolean isFailure() {
        return this instanceof AsyncFailure<V, E>;
    }

    /**
     * Checks if this AsyncVessel is still pending (async computation).
     *
     * @return true if this is an AsyncSuccess (which may or may not have completed)
     */
    default boolean isPending() {
        return this instanceof AsyncPending<V, E>;
    }

    /**
     * Unwraps CompletionException and ExecutionException to get the root cause.
     */
    private static Throwable unwrapException(Throwable throwable) {
        Throwable cause = throwable;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
