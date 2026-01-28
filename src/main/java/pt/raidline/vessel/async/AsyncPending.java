package pt.raidline.vessel.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Represents an async operation that wraps a CompletionStage.
 * <p>
 * The underlying stage may be pending, completed successfully, or completed exceptionally.
 * Use {@link AsyncVessel#onComplete(java.util.function.Consumer)} to handle the result.
 *
 * @param <V> the value type
 * @param <E> the error type
 */
public record AsyncPending<V, E extends Exception>(CompletionStage<V> stage) implements AsyncVessel<V, E> {

    /**
     * Blocks until the future completes and returns the value.
     *
     * @return the completed value
     * @throws ExecutionException   if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     */
    public V block() throws ExecutionException, InterruptedException {
        return stage.toCompletableFuture().get();
    }

    /**
     * Blocks until the future completes with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return the completed value
     * @throws ExecutionException   if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted
     * @throws TimeoutException     if the wait timed out
     */
    public V block(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        return stage.toCompletableFuture().get(timeout, unit);
    }

    /**
     * Returns the underlying CompletableFuture.
     *
     * @return the CompletableFuture
     */
    public CompletableFuture<V> toCompletableFuture() {
        return stage.toCompletableFuture();
    }

    /**
     * Returns the value if already completed, otherwise null.
     * <p>
     * This is a non-blocking check. Returns null if:
     * <ul>
     *   <li>The future is not yet complete</li>
     *   <li>The future completed exceptionally</li>
     * </ul>
     *
     * @return the value if completed successfully, null otherwise
     */
    public V valueOrNull() {
        CompletableFuture<V> future = stage.toCompletableFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            try {
                return future.getNow(null);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
