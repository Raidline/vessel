package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Vessel.success()")
class SuccessFactoryTest {

    @Test
    @DisplayName("should create a Success instance with the given value")
    void shouldCreateSuccessWithValue() {
        var vessel = Vessel.success("hello");

        switch (vessel) {
            case Success(var value) -> assertEquals("hello", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }
}
