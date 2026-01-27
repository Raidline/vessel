package pt.raidline.vessel;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public record Failure<V, E extends Exception>(E ex) implements Vessel<V, E> {

    public E getError() {
        return ex;
    }

    public V replace(V defaultValue) {
        Objects.requireNonNull(defaultValue);

        return defaultValue;
    }

    public V replaceGet(Supplier<V> computation) {
        Objects.requireNonNull(computation);

        var v = computation.get();

        Objects.requireNonNull(v);

        return v;
    }

    public void raise() throws E {
        Objects.requireNonNull(ex);
        throw ex;
    }

    public Failure<V, E> peekError(Consumer<E> consumer) {
        consumer.accept(this.ex);
        return this;
    }

    public <T> Vessel<T, E> recover(Function<E, T> transformer) {
        Objects.requireNonNull(transformer);

        return new Success<>(transformer.apply(ex));
    }
}
