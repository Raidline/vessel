package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.raidline.vessel.exception.MergeZipFailureException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("zip()")
class ZipTest {

    @Test
    @DisplayName("should combine two Success vessels using merger function")
    void shouldCombineTwoSuccessVessels() {
        var first = Vessel.<Integer, RuntimeException>success(10);
        var second = Vessel.<Integer, RuntimeException>success(20);

        var result = Vessel.zip(first, second, Integer::sum);

        switch (result) {
            case Success(var value) -> assertEquals(30, value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should combine two Success vessels with different types")
    void shouldCombineTwoSuccessVesselsWithDifferentTypes() {
        var first = Vessel.<String, RuntimeException>success("Hello");
        var second = Vessel.<Integer, RuntimeException>success(42);

        var result = Vessel.zip(first, second, (s, i) -> s + ": " + i);

        switch (result) {
            case Success(var value) -> assertEquals("Hello: 42", value);
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return MergeZipFailureException when first is Failure")
    void shouldReturnMergeZipFailureExceptionWhenFirstIsFailure() {
        IOException exception = new IOException("first failed");
        var first = Vessel.<Integer, Exception>failure(exception);
        var second = Vessel.success(20);

        var result = Vessel.zip(first, second, Integer::sum);

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> {
                assertInstanceOf(MergeZipFailureException.class, err);
                MergeZipFailureException mergeEx = (MergeZipFailureException) err;
                assertEquals(exception, mergeEx.first);
                assertNull(mergeEx.second);
                assertEquals("First vessel has failed", mergeEx.getMessage());
            }
        }
    }

    @Test
    @DisplayName("should return MergeZipFailureException when second is Failure")
    void shouldReturnMergeZipFailureExceptionWhenSecondIsFailure() {
        IOException exception = new IOException("second failed");
        var first = Vessel.success(10);
        var second = Vessel.<Integer, Exception>failure(exception);

        var result = Vessel.zip(first, second, Integer::sum);

        switch (result) {
            case Success(var value) -> fail("Expected Failure");
            case Failure(var err) -> {
                assertInstanceOf(MergeZipFailureException.class, err);
                MergeZipFailureException mergeEx = (MergeZipFailureException) err;
                // Note: Due to mapError implementation, the error is passed as 'first' parameter
                assertEquals(exception, mergeEx.first);
                assertNull(mergeEx.second);
                assertEquals("Second vessel has failed", mergeEx.getMessage());
            }
        }
    }

    @Test
    @DisplayName("should return MergeZipFailureException when both are Failure")
    void shouldReturnMergeZipFailureExceptionWhenBothAreFailure() {
        IOException firstException = new IOException("first failed");
        IOException secondException = new IOException("second failed");
        var first = Vessel.<Integer, Exception>failure(firstException);
        var second = Vessel.<Integer, Exception>failure(secondException);

        var result = Vessel.zip(first, second, Integer::sum);

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
        var second = Vessel.<Integer, RuntimeException>success(20);

        assertThrows(NullPointerException.class, () ->
                Vessel.zip(null, second, Integer::sum)
        );
    }

    @Test
    @DisplayName("should throw NullPointerException when second is null")
    void shouldThrowNullPointerExceptionWhenSecondIsNull() {
        var first = Vessel.<Integer, RuntimeException>success(10);

        assertThrows(NullPointerException.class, () ->
                Vessel.zip(first, null, Integer::sum)
        );
    }

    @Test
    @DisplayName("should throw NullPointerException when merger is null")
    void shouldThrowNullPointerExceptionWhenMergerIsNull() {
        var first = Vessel.<Integer, RuntimeException>success(10);
        var second = Vessel.<Integer, RuntimeException>success(20);

        assertThrows(NullPointerException.class, () ->
                Vessel.zip(first, second, null)
        );
    }

    @Test
    @DisplayName("should create complex merged result from two Success vessels")
    void shouldCreateComplexMergedResult() {
        record Person(String name, int age) {
        }

        var nameVessel = Vessel.<String, RuntimeException>success("Alice");
        var ageVessel = Vessel.<Integer, RuntimeException>success(30);

        var result = Vessel.zip(nameVessel, ageVessel, Person::new);

        switch (result) {
            case Success(var person) -> {
                assertEquals("Alice", person.name());
                assertEquals(30, person.age());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }
}
