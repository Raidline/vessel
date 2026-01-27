package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("mapError()")
class MapErrorTest {

    @Test
    @DisplayName("should not change error when Success")
    void shouldNotChangeErrorWhenSuccess() {
        var vessel = Vessel.<String, IOException>success("hello");

        var result = vessel.mapError(e -> new RuntimeException(e.getMessage()));

        switch (result) {
            case Success(var value) -> assertEquals("hello", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should transform error when Failure")
    void shouldTransformErrorWhenFailure() {
        IOException original = new IOException("original error");
        var vessel = Vessel.<String, IOException>failure(original);

        var result = vessel.mapError(e -> new IllegalStateException("Wrapped: " + e.getMessage()));

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> {
                assertInstanceOf(IllegalStateException.class, err);
                assertEquals("Wrapped: original error", err.getMessage());
            }
        }
    }
}
