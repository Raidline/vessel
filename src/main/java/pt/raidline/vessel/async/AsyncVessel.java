package pt.raidline.vessel.async;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface AsyncVessel<V, E extends Exception> permits AsyncSuccess, AsyncFailure {

    static <F extends CompletionStage<V>, V, E extends Exception> AsyncVessel<V, E> lift(Supplier<F> async) {
        return null;
    }

    default <R> AsyncVessel<R, E> mapAsync(Function<V, R> mapper) {
        return null;
    }

    default <T extends Exception> AsyncVessel<V, T> mapErrorAsync(Function<E, T> mapper) {
        return null;
    }

    default <R, T extends Exception> AsyncVessel<R, T> flatMapAsync(Function<? super V, ? extends AsyncVessel<R, T>> mapper) {
        return null;
    }

    default void onComplete(Consumer<AsyncVessel<V, E>> consumer) {
        consumer.accept(this);
    }
}
