package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Combining map() and flatMap()")
class CombinedOperationsTest {

    @Test
    @DisplayName("should combine map and flatMap operations on Success")
    void shouldCombineMapAndFlatMapOnSuccess() {
        var vessel = Vessel.<String, RuntimeException>success("hello");

        var result = vessel
                .map(String::toUpperCase)
                .flatMap(s -> Vessel.success(s + "!"))
                .map(String::length)
                .flatMap(len -> Vessel.success("Length: " + len));

        switch (result) {
            case Success(var value) -> assertEquals("Length: 6", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should stop at failure in combined operations")
    void shouldStopAtFailureInCombinedOperations() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>success("hello");

        var result = vessel
                .map(String::toUpperCase)
                .flatMap(s -> Vessel.<String, IOException>failure(exception))
                .map(String::length)
                .flatMap(len -> Vessel.success("Length: " + len));

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }
}
