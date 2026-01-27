package pt.raidline.vessel;

import pt.raidline.vessel.exception.MergeZipFailureException;
import pt.raidline.vessel.exception.ValueNotPresentException;
import pt.raidline.vessel.lambdas.Throwing;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public sealed interface Vessel<V, E extends Exception> permits Failure, Success {

    static <V, E extends Exception> Vessel<V, E> lift(Throwing<V, E> throwing) {
        try {
            var v = throwing.get();
            return success(v);
        } catch (Exception ex) {
            return failure((E) ex);
        }
    }

    static <V, E extends Exception> Vessel<V, E> success(V value) {
        Objects.requireNonNull(value, "You cannot pass a [null] value as a success");
        return new Success<>(value);
    }

    static <V, E extends Exception> Vessel<V, E> failure(E err) {
        Objects.requireNonNull(err, "You cannot pass a [null] value as an err");
        return new Failure<>(err);
    }

    static <R, T, V, E extends Exception> Vessel<R, E> zip(Vessel<V, E> first,
                                                           Vessel<T, E> second,
                                                           BiFunction<V, T, R> merger) {

        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        Objects.requireNonNull(merger);

        if (first.isFailure() && second.isFailure()) {
            var f1 = (Failure<V, E>) first;
            var f2 = (Failure<T, E>) second;

            return new Failure<>((E) new MergeZipFailureException(
                    "Both of the vessels are failure types",
                    f1.ex(),
                    f2.ex()
            ));
        }

        if (first.isFailure()) {
            return (Vessel<R, E>) first.mapError(e -> new MergeZipFailureException("First vessel has failed",
                    e,
                    null));
        }

        if (second.isFailure()) {
            return (Vessel<R, E>) second.mapError(e -> new MergeZipFailureException("Second vessel has failed",
                    e,
                    null));
        }

        var s1 = (Success<V, E>) first;
        var s2 = (Success<T, E>) second;

        return success(merger.apply(s1.value(), s2.value()));
    }

    static <V, E extends Exception> Vessel<V, E> oneOf(Vessel<V, E> first, Vessel<V, E> second) {

        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        if (first.isFailure() && second.isFailure()) {
            var f1 = (Failure<V, E>) first;
            var f2 = (Failure<V, E>) second;

            return new Failure<>((E) new MergeZipFailureException(
                    "Both of the vessels are failure types",
                    f1.ex(),
                    f2.ex()
            ));
        }

        if (first.isFailure()) {
            return second;
        }

        return first;
    }

    default boolean isSuccess() {
        return this instanceof Success<V, E>;
    }

    default boolean isFailure() {
        return this instanceof Failure<V, E>;
    }

    default <R> Vessel<R, E> map(Function<V, R> mapper) {
        if (this instanceof Success<V, E>(V value)) {
            return new Success<>(mapper.apply(value));
        }

        return (Vessel<R, E>) this;
    }

    default <T extends Exception> Vessel<V, T> mapError(Function<E, T> mapper) {
        if (this instanceof Failure<V, E>(E err)) {
            return new Failure<>(mapper.apply(err));
        }

        return (Vessel<V, T>) this;
    }

    default <R, T extends Exception> Vessel<R, T> flatMap(Function<? super V, ? extends Vessel<R, T>> mapper) {
        if (this instanceof Success<V, E>(V value)) {
            return mapper.apply(value);
        }

        return (Vessel<R, T>) this;
    }

    default <R> R fold(Function<V, R> onSuccess, Function<E, R> onError) {
        return switch (this) {
            case Success(var value) -> onSuccess.apply(value);
            case Failure(var err) -> onError.apply(err);
        };
    }

    default <T extends Exception> Vessel<V, T> filter(Predicate<V> predicate, Supplier<T> throwable) {
        if (this instanceof Failure<V, E>) {
            return (Vessel<V, T>) this;
        }

        var success = (Success<V, E>) this;

        if (predicate.test(success.value())) {
            return (Vessel<V, T>) this;
        }

        return new Failure<>(throwable.get());
    }

    default V unwrap() {
        if (this instanceof Failure<V, E>) {
            throw new ValueNotPresentException("This Vessel is of type failure, you just disappointed a Rust dev " +
                    "somewhere");
        }

        return ((Success<V, E>) this).get();
    }

    default <T> Vessel<T, E> recoverWith(Function<E, Vessel<T, E>> recovered) {
        Objects.requireNonNull(recovered);

        if (this instanceof Failure<V, E>(var ex)) {
            return recovered.apply(ex);
        }

        return (Vessel<T, E>) this;
    }
}
