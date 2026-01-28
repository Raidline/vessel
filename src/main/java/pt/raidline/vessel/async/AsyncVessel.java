package pt.raidline.vessel.async;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public sealed interface AsyncVessel<V, E extends Exception> permits AsyncSuccess, AsyncFailure {

    static <V, E extends Exception> AsyncVessel<V, E> lift(Supplier<CompletionStage<V>> async) {
        CompletableFuture<V> wrapper = new CompletableFuture<>();
        async.get().whenComplete((v, th) -> {
            if (th != null) {
                wrapper.completeExceptionally(th);
            } else {
                wrapper.complete(v);
            }
        });

        return new AsyncSuccess<>(wrapper);
    }

    default <R> AsyncVessel<R, E> mapAsync(Function<V, R> mapper) {
        if (this instanceof AsyncSuccess<V, E>(var value)) {
            return new AsyncSuccess<>(value.thenApply(mapper));
        }

        return (AsyncVessel<R, E>) this;
    }

    default <T extends Exception> AsyncVessel<V, T> mapErrorAsync(Function<E, T> mapper) {
        return null;
    }

    default <R, T extends Exception> AsyncVessel<R, T> flatMapAsync(Function<? super V, ? extends CompletionStage<R>> mapper) {
        if (this instanceof AsyncSuccess<V, E>(var future)) {
            return new AsyncSuccess<>(future.thenCompose(mapper));
        }

        return (AsyncVessel<R, T>) this;
    }

    default <R, T extends Exception> AsyncVessel<R, T> flatMap(Function<? super V, ? extends AsyncVessel<R, T>> mapper) {
        if (this instanceof AsyncSuccess<V, E>(var future)) {
            //todo:
            /*return lift(() -> {
                return future.thenApply(v -> mapper.apply(v));
            });*/
        }

        return (AsyncVessel<R, T>) this;
    }

    default void onComplete(Consumer<AsyncVessel<V, E>> consumer) {
        consumer.accept(this);
    }
}
