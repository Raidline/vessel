package pt.raidline.vessel.exception;

public class MergeZipFailureException extends Exception {
    public final Exception first;
    public final Exception second;

    public MergeZipFailureException(String message, Exception first, Exception second) {
        super(message);
        this.first = first;
        this.second = second;
    }
}
