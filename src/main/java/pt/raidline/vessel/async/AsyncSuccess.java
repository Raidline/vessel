package pt.raidline.vessel.async;

import java.util.concurrent.CompletionStage;

public record AsyncSuccess<V, E extends Exception>(CompletionStage<V> future) implements AsyncVessel<V, E> {
}
