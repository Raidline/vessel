package pt.raidline.vessel.lambdas;

@FunctionalInterface
public interface Throwing<V, E extends Exception> {
    V get() throws E;
}
