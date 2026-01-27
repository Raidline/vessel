package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Vessel.failure()")
class FailureFactoryTest {

    @Test
    @DisplayName("should create a Failure instance with the given exception")
    void shouldCreateFailureWithException() {
        IOException exception = new IOException("test error");
        var vessel = Vessel.<String, IOException>failure(exception);

        switch (vessel) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }
}
