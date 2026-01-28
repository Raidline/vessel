package pt.raidline.vessel.exception;

public class AsyncUnwrapException extends RuntimeException {
    public AsyncUnwrapException(String message) {
        super(message);
    }

    public AsyncUnwrapException(String message, Throwable cause) {
        super(message, cause);
    }
}
