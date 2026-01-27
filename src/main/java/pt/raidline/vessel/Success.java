package pt.raidline.vessel;


import java.util.Objects;
import java.util.function.Consumer;

public record Success<V, E extends Exception>(V value) implements Vessel<V, E> {

    public V get() {
        Objects.requireNonNull(value);

        return this.value;
    }

    public Success<V, E> peek(Consumer<V> peeker) {
        Objects.requireNonNull(peeker);

        peeker.accept(value);
        return this;
    }
}
