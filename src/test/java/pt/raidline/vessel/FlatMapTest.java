package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("flatMap()")
class FlatMapTest {

    @Test
    @DisplayName("should apply mapper function and flatten when Success")
    void shouldApplyMapperAndFlattenWhenSuccess() {
        var vessel = Vessel.<String, RuntimeException>success("hello");

        var result = vessel.flatMap(s -> Vessel.success(s.length()));

        switch (result) {
            case Success(var value) -> assertEquals(5, value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when mapper returns Failure on Success")
    void shouldReturnFailureWhenMapperReturnsFailure() {
        IOException exception = new IOException("mapped error");
        var vessel = Vessel.<String, IOException>success("hello");

        var result = vessel.flatMap(s -> Vessel.failure(exception));

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should chain multiple flatMaps on Success")
    void shouldChainMultipleFlatMapsOnSuccess() {
        var vessel = Vessel.<Integer, RuntimeException>success(10);

        var result = vessel
                .flatMap(n -> Vessel.success(n * 2))
                .flatMap(n -> Vessel.success("Result: " + n));

        switch (result) {
            case Success(var value) -> assertEquals("Result: 20", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should short-circuit at first Failure in chain")
    void shouldShortCircuitAtFirstFailure() {
        IOException exception = new IOException("first failure");
        var vessel = Vessel.<Integer, IOException>success(10);

        var result = vessel
                .flatMap(n -> Vessel.failure(exception))
                .flatMap(n -> Vessel.success("should not reach"));

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }
}
