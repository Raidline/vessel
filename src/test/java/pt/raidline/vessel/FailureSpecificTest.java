package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Failure-specific methods")
class FailureSpecificTest {

    @Test
    @DisplayName("getError() should return the exception")
    void getErrorShouldReturnException() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> assertEquals(exception, failure.getError());
        }
    }

    @Test
    @DisplayName("replace() should return the default value")
    void replaceShouldReturnDefault() {
        var vessel = Vessel.<String, IOException>failure(new IOException());

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> assertEquals("default", failure.replace("default"));
        }
    }

    @Test
    @DisplayName("replaceGet() should compute and return default")
    void replaceGetShouldComputeDefault() {
        var vessel = Vessel.<String, IOException>failure(new IOException());

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                String result = failure.replaceGet(() -> "computed-" + 42);
                assertEquals("computed-42", result);
            }
        }
    }

    @Test
    @DisplayName("raise() should throw the exception")
    void raiseShouldThrowException() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                IOException thrown = assertThrows(IOException.class, failure::raise);
                assertEquals(exception, thrown);
            }
        }
    }

    @Test
    @DisplayName("peekError() should execute consumer and return Failure")
    void peekErrorShouldExecuteConsumer() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);
        List<Exception> sideEffects = new ArrayList<>();

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                var result = failure.peekError(sideEffects::add);
                assertEquals(List.of(exception), sideEffects);
                assertSame(failure, result);
            }
        }
    }

    @Test
    @DisplayName("recover() should return Success with recovered value")
    void recoverShouldReturnSuccess() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                var result = failure.recover(e -> "recovered from: " + e.getMessage());
                switch (result) {
                    case Success(var recoveredValue) -> assertEquals("recovered from: error", recoveredValue);
                    case Failure(var err) -> fail("Expected Success after recovery");
                }
            }
        }
    }

    @Test
    @DisplayName("recoverWith() should return result of recovery function")
    void recoverWithShouldReturnRecoveryResult() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                var result = failure.recoverWith(e -> Vessel.success("recovered"));
                switch (result) {
                    case Success(var recoveredValue) -> assertEquals("recovered", recoveredValue);
                    case Failure(var err) -> fail("Expected Success after recovery");
                }
            }
        }
    }

    @Test
    @DisplayName("recoverWith() can return another Failure")
    void recoverWithCanReturnFailure() {
        IOException originalError = new IOException("original");
        IOException recoveryError = new IOException("recovery failed");
        var vessel = Vessel.<String, IOException>failure(originalError);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure<String, IOException> failure -> {
                var result = failure.recoverWith(e -> Vessel.failure(recoveryError));
                switch (result) {
                    case Success(var value2) -> fail("Expected Failure");
                    case Failure(var err) -> assertEquals(recoveryError, err);
                }
            }
        }
    }
}
