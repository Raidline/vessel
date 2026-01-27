package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Success-specific methods")
class SuccessSpecificTest {

    @Test
    @DisplayName("get() should return the value")
    void getShouldReturnValue() {
        var vessel = Vessel.success("hello");

        switch (vessel) {
            case Success<String, ?> success -> assertEquals("hello", success.get());
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("peek() should execute consumer and return Success")
    void peekShouldExecuteConsumer() {
        var vessel = Vessel.success("hello");
        List<String> sideEffects = new ArrayList<>();

        switch (vessel) {
            case Success<String, ?> success -> {
                var result = success.peek(sideEffects::add);
                assertEquals(List.of("hello"), sideEffects);
                assertSame(success, result);
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }
}
