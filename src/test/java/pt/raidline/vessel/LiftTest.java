package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Vessel.lift()")
class LiftTest {

    @Test
    @DisplayName("should return Success when throwing lambda succeeds")
    void shouldReturnSuccessWhenLambdaSucceeds() {
        var vessel = Vessel.lift(() -> "result");

        switch (vessel) {
            case Success(var value) -> assertEquals("result", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when throwing lambda throws exception")
    void shouldReturnFailureWhenLambdaThrows() {
        IOException expectedException = new IOException("file not found");
        Vessel<String, IOException> vessel = Vessel.lift(() -> {
            throw expectedException;
        });

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(expectedException, err);
        }
    }

    @Test
    @DisplayName("should return Failure when throwing lambda throws runtime exception")
    void shouldReturnFailureWhenLambdaThrowsRuntimeException() {
        RuntimeException expectedException = new IllegalArgumentException("invalid");
        Vessel<String, RuntimeException> vessel = Vessel.lift(() -> {
            throw expectedException;
        });

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(expectedException, err);
        }
    }
}
