package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("fold()")
class FoldTest {

    @Test
    @DisplayName("should apply success function when Success")
    void shouldApplySuccessFunctionWhenSuccess() {
        var vessel = Vessel.<Integer, RuntimeException>success(42);

        String result = vessel.fold(
                v -> "Value: " + v,
                e -> "Error: " + e.getMessage()
        );

        assertEquals("Value: 42", result);
    }

    @Test
    @DisplayName("should apply failure function when Failure")
    void shouldApplyFailureFunctionWhenFailure() {
        IOException error = new IOException("something went wrong");
        var vessel = Vessel.<Integer, IOException>failure(error);

        String result = vessel.fold(
                v -> "Value: " + v,
                e -> "Error: " + e.getMessage()
        );

        assertEquals("Error: something went wrong", result);
    }
}
