package pt.raidline.vessel.async;

public record AsyncFailure<V, E extends Exception>(E ex) implements AsyncVessel<V, E> {

}
