package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("map()")
class MapTest {

    @Test
    @DisplayName("should apply mapper function when Success")
    void shouldApplyMapperWhenSuccess() {
        var vessel = Vessel.<String, RuntimeException>success("hello");

        var result = vessel.map(String::length);

        switch (result) {
            case Success(var value) -> assertEquals(5, value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should chain multiple maps on Success")
    void shouldChainMultipleMapsOnSuccess() {
        var vessel = Vessel.<String, RuntimeException>success("hello");

        var result = vessel
                .map(String::length)
                .map(len -> len * 2)
                .map(Object::toString);

        switch (result) {
            case Success(var value) -> assertEquals("10", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should not apply mapper when Failure")
    void shouldNotApplyMapperWhenFailure() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        var result = vessel.map(String::length);

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should preserve failure through multiple maps")
    void shouldPreserveFailureThroughMultipleMaps() {
        IOException exception = new IOException("error");
        var vessel = Vessel.<String, IOException>failure(exception);

        var result = vessel
                .map(String::length)
                .map(len -> len * 2)
                .map(Object::toString);

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }
}
