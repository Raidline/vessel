package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.raidline.vessel.exception.MergeZipFailureException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("oneOf()")
class OneOfTest {

    @Test
    @DisplayName("should return first when both are Success")
    void shouldReturnFirstWhenBothAreSuccess() {
        var first = Vessel.<String, RuntimeException>success("first");
        var second = Vessel.<String, RuntimeException>success("second");

        var result = Vessel.oneOf(first, second);

        switch (result) {
            case Success(var value) -> assertEquals("first", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return first when first is Success and second is Failure")
    void shouldReturnFirstWhenFirstIsSuccessAndSecondIsFailure() {
        var first = Vessel.<String, IOException>success("first");
        var second = Vessel.<String, IOException>failure(new IOException("error"));

        var result = Vessel.oneOf(first, second);

        switch (result) {
            case Success(var value) -> assertEquals("first", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return second when first is Failure and second is Success")
    void shouldReturnSecondWhenFirstIsFailureAndSecondIsSuccess() {
        var first = Vessel.<String, IOException>failure(new IOException("error"));
        var second = Vessel.<String, IOException>success("second");

        var result = Vessel.oneOf(first, second);

        switch (result) {
            case Success(var value) -> assertEquals("second", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return MergeZipFailureException when both are Failure")
    void shouldReturnMergeZipFailureExceptionWhenBothAreFailure() {
        IOException firstException = new IOException("first failed");
        IOException secondException = new IOException("second failed");
        var first = Vessel.<String, Exception>failure(firstException);
        var second = Vessel.<String, Exception>failure(secondException);

        var result = Vessel.oneOf(first, second);

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> {
                assertInstanceOf(MergeZipFailureException.class, err);
                MergeZipFailureException mergeEx = (MergeZipFailureException) err;
                assertEquals(firstException, mergeEx.first);
                assertEquals(secondException, mergeEx.second);
                assertEquals("Both of the vessels are failure types", mergeEx.getMessage());
            }
        }
    }

    @Test
    @DisplayName("should throw NullPointerException when first is null")
    void shouldThrowNullPointerExceptionWhenFirstIsNull() {
        var second = Vessel.<String, RuntimeException>success("second");

        assertThrows(NullPointerException.class, () ->
                Vessel.oneOf(null, second)
        );
    }

    @Test
    @DisplayName("should throw NullPointerException when second is null")
    void shouldThrowNullPointerExceptionWhenSecondIsNull() {
        var first = Vessel.<String, RuntimeException>success("first");

        assertThrows(NullPointerException.class, () ->
                Vessel.oneOf(first, null)
        );
    }

    @Test
    @DisplayName("should prefer first Success over second Success")
    void shouldPreferFirstSuccessOverSecondSuccess() {
        var first = Vessel.<Integer, RuntimeException>success(1);
        var second = Vessel.<Integer, RuntimeException>success(2);

        var result = Vessel.oneOf(first, second);

        assertSame(first, result);
    }

    @Test
    @DisplayName("should work with complex types")
    void shouldWorkWithComplexTypes() {
        record User(String name) {
        }

        var first = Vessel.<User, RuntimeException>failure(new RuntimeException("not found"));
        var second = Vessel.<User, RuntimeException>success(new User("Alice"));

        var result = Vessel.oneOf(first, second);

        switch (result) {
            case Success(var user) -> assertEquals("Alice", user.name());
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should be useful for fallback scenarios")
    void shouldBeUsefulForFallbackScenarios() {
        // Simulating: try primary source, fallback to secondary
        var primaryResult = Vessel.<String, IOException>failure(new IOException("primary unavailable"));
        var fallbackResult = Vessel.<String, IOException>success("fallback value");

        var result = Vessel.oneOf(primaryResult, fallbackResult);

        switch (result) {
            case Success(var value) -> assertEquals("fallback value", value);
            case Failure(var err) -> fail("Expected Success from fallback");
        }
    }
}
