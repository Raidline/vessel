package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.raidline.vessel.exception.ValueNotPresentException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("filter()")
class FilterTest {

    @Test
    @DisplayName("should keep Success when predicate passes")
    void shouldKeepSuccessWhenPredicatePasses() {
        var vessel = Vessel.<Integer, IllegalArgumentException>success(42);

        var result = vessel.filter(
                v -> v > 0,
                () -> new IllegalArgumentException("must be positive")
        );

        switch (result) {
            case Success(var value) -> assertEquals(42, value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should convert to Failure when predicate fails")
    void shouldConvertToFailureWhenPredicateFails() {
        var vessel = Vessel.<Integer, IllegalArgumentException>success(-5);

        var result = vessel.filter(
                v -> v > 0,
                () -> new IllegalArgumentException("must be positive")
        );

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals("must be positive", err.getMessage());
        }
    }

    @Test
    @DisplayName("should keep Failure unchanged")
    void shouldKeepFailureUnchanged() {
        IllegalArgumentException originalError = new IllegalArgumentException("original");
        var vessel = Vessel.<Integer, IllegalArgumentException>failure(originalError);

        var result = vessel.filter(
                v -> v > 0,
                () -> new IllegalArgumentException("filter error")
        );

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(originalError, err);
        }
    }

    @Test
    @DisplayName("should throw exception if unwrap on a failure")
    void shouldThrowExceptionIfUnwrapOnAFailure() {
        IllegalArgumentException originalError = new IllegalArgumentException("original");
        var vessel = Vessel.<Integer, IllegalArgumentException>failure(originalError);

        var err = assertThrowsExactly(ValueNotPresentException.class,
                vessel::unwrap);

        assertNotNull(err);
    }

    @Test
    @DisplayName("should not throw exception if unwrap on a success")
    void shouldNotThrowExceptionIfUnwrapOnASuccess() {
        var vessel = Vessel.<Integer, IllegalArgumentException>success(69);

        var value = vessel.unwrap();

        assertEquals(69, value);
    }
}
