package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pt.raidline.vessel.exception.MergeZipFailureException;
import pt.raidline.vessel.exception.ValueNotPresentException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class VesselTest {

    // ==================== STATIC FACTORY TESTS ====================

    @Nested
    @DisplayName("Vessel.success()")
    class SuccessFactoryTests {

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

    @Nested
    @DisplayName("Vessel.failure()")
    class FailureFactoryTests {

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

    @Nested
    @DisplayName("Vessel.lift()")
    class LiftTests {

        @Test
        @DisplayName("should return Success when throwing lambda succeeds")
        void shouldReturnSuccessWhenLambdaSucceeds() {
            var vessel = Vessel.lift(() -> "result");

            switch (vessel) {
                case Success(var value) -> assertEquals("result", value);
                case Failure(var err) -> fail("Expected Success");
            }
        }

        @Test
        @DisplayName("should return Failure when throwing lambda throws exception")
        void shouldReturnFailureWhenLambdaThrows() {
            IOException expectedException = new IOException("file not found");
            Vessel<String, IOException> vessel = Vessel.lift(() -> {
                throw expectedException;
            });

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure(var err) -> assertEquals(expectedException, err);
            }
        }

        @Test
        @DisplayName("should return Failure when throwing lambda throws runtime exception")
        void shouldReturnFailureWhenLambdaThrowsRuntimeException() {
            RuntimeException expectedException = new IllegalArgumentException("invalid");
            Vessel<String, RuntimeException> vessel = Vessel.lift(() -> {
                throw expectedException;
            });

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure(var err) -> assertEquals(expectedException, err);
            }
        }
    }

    // ==================== INTERFACE METHOD TESTS (Polymorphic) ====================

    @Nested
    @DisplayName("isSuccess() / isFailure()")
    class TypeCheckTests {

        @Test
        @DisplayName("Success should return true for isSuccess and false for isFailure")
        void successTypeChecks() {
            var vessel = Vessel.success("value");

            assertTrue(vessel.isSuccess());
            assertFalse(vessel.isFailure());
        }

        @Test
        @DisplayName("Failure should return false for isSuccess and true for isFailure")
        void failureTypeChecks() {
            var vessel = Vessel.<String, IOException>failure(new IOException());

            assertFalse(vessel.isSuccess());
            assertTrue(vessel.isFailure());
        }
    }

    @Nested
    @DisplayName("map()")
    class MapTests {

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

    @Nested
    @DisplayName("flatMap()")
    class FlatMapTests {

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

    @Nested
    @DisplayName("fold()")
    class FoldTests {

        @Test
        @DisplayName("should apply success function when Success")
        void shouldApplySuccessFunctionWhenSuccess() {
            var vessel = Vessel.<Integer, RuntimeException>success(42);

            String result = vessel.fold(
                    v -> "Value: " + v,
                    e -> "Error: " + e.getMessage()
            );

            assertEquals("Value: 42", result);
        }

        @Test
        @DisplayName("should apply failure function when Failure")
        void shouldApplyFailureFunctionWhenFailure() {
            IOException error = new IOException("something went wrong");
            var vessel = Vessel.<Integer, IOException>failure(error);

            String result = vessel.fold(
                    v -> "Value: " + v,
                    e -> "Error: " + e.getMessage()
            );

            assertEquals("Error: something went wrong", result);
        }
    }

    @Nested
    @DisplayName("filter()")
    class FilterTests {

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

    @Nested
    @DisplayName("mapError()")
    class MapErrorTests {

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

    @Nested
    @DisplayName("zip()")
    class ZipTests {

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
            var second = Vessel.<Integer, Exception>success(20);

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
            var first = Vessel.<Integer, Exception>success(10);
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
            record Person(String name, int age) {}

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

    @Nested
    @DisplayName("oneOf()")
    class OneOfTests {

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
            record User(String name) {}

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

    // ==================== SUCCESS-SPECIFIC METHOD TESTS ====================

    @Nested
    @DisplayName("Success-specific methods")
    class SuccessSpecificTests {

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

    // ==================== FAILURE-SPECIFIC METHOD TESTS ====================

    @Nested
    @DisplayName("Failure-specific methods")
    class FailureSpecificTests {

        @Test
        @DisplayName("getError() should return the exception")
        void getErrorShouldReturnException() {
            IOException exception = new IOException("error");
            var vessel = Vessel.<String, IOException>failure(exception);

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> assertEquals(exception, failure.getError());
            }
        }

        @Test
        @DisplayName("replace() should return the default value")
        void replaceShouldReturnDefault() {
            var vessel = Vessel.<String, IOException>failure(new IOException());

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> assertEquals("default", failure.replace("default"));
            }
        }

        @Test
        @DisplayName("replaceGet() should compute and return default")
        void replaceGetShouldComputeDefault() {
            var vessel = Vessel.<String, IOException>failure(new IOException());

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    String result = failure.replaceGet(() -> "computed-" + 42);
                    assertEquals("computed-42", result);
                }
            }
        }

        @Test
        @DisplayName("raise() should throw the exception")
        void raiseShouldThrowException() {
            IOException exception = new IOException("error");
            var vessel = Vessel.<String, IOException>failure(exception);

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    IOException thrown = assertThrows(IOException.class, failure::raise);
                    assertEquals(exception, thrown);
                }
            }
        }

        @Test
        @DisplayName("peekError() should execute consumer and return Failure")
        void peekErrorShouldExecuteConsumer() {
            IOException exception = new IOException("error");
            var vessel = Vessel.<String, IOException>failure(exception);
            List<Exception> sideEffects = new ArrayList<>();

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    var result = failure.peekError(sideEffects::add);
                    assertEquals(List.of(exception), sideEffects);
                    assertSame(failure, result);
                }
            }
        }

        @Test
        @DisplayName("recover() should return Success with recovered value")
        void recoverShouldReturnSuccess() {
            IOException exception = new IOException("error");
            var vessel = Vessel.<String, IOException>failure(exception);

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    var result = failure.recover(e -> "recovered from: " + e.getMessage());
                    switch (result) {
                        case Success(var recoveredValue) -> assertEquals("recovered from: error", recoveredValue);
                        case Failure(var err) -> fail("Expected Success after recovery");
                    }
                }
            }
        }

        @Test
        @DisplayName("recoverWith() should return result of recovery function")
        void recoverWithShouldReturnRecoveryResult() {
            IOException exception = new IOException("error");
            var vessel = Vessel.<String, IOException>failure(exception);

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    var result = failure.recoverWith(e -> Vessel.success("recovered"));
                    switch (result) {
                        case Success(var recoveredValue) -> assertEquals("recovered", recoveredValue);
                        case Failure(var err) -> fail("Expected Success after recovery");
                    }
                }
            }
        }

        @Test
        @DisplayName("recoverWith() can return another Failure")
        void recoverWithCanReturnFailure() {
            IOException originalError = new IOException("original");
            IOException recoveryError = new IOException("recovery failed");
            var vessel = Vessel.<String, IOException>failure(originalError);

            switch (vessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    var result = failure.recoverWith(e -> Vessel.failure(recoveryError));
                    switch (result) {
                        case Success(var value2) -> fail("Expected Failure");
                        case Failure(var err) -> assertEquals(recoveryError, err);
                    }
                }
            }
        }
    }

    // ==================== COMBINED OPERATIONS TESTS ====================

    @Nested
    @DisplayName("Combining map() and flatMap()")
    class CombinedOperationsTests {

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

    // ==================== REAL-WORLD SCENARIO TESTS ====================

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        // Simulated domain exceptions
        static class UserNotFoundException extends Exception {
            UserNotFoundException(String message) {
                super(message);
            }
        }

        static class ValidationException extends Exception {
            ValidationException(String message) {
                super(message);
            }
        }

        // Simulated domain objects
        record User(String id, String name, String email, int age) {
        }

        record UserDTO(String displayName, String contactEmail) {
        }

        // Simulated repository/service methods
        Vessel<User, UserNotFoundException> findUserById(String id) {
            if ("123".equals(id)) {
                return Vessel.success(new User("123", "John Doe", "john@example.com", 30));
            }
            return Vessel.failure(new UserNotFoundException("User not found: " + id));
        }

        @Test
        @DisplayName("Scenario: User lookup and transformation using pattern matching")
        void userLookupWithPatternMatching() {
            var result = findUserById("123")
                    .map(user -> new UserDTO(user.name().toUpperCase(), user.email()));

            String message = switch (result) {
                case Success(var dto) -> "Found: " + dto.displayName();
                case Failure(var err) -> "Error: " + err.getMessage();
            };

            assertEquals("Found: JOHN DOE", message);
        }

        @Test
        @DisplayName("Scenario: User lookup failure with pattern matching")
        void userLookupFailureWithPatternMatching() {
            var result = findUserById("unknown");

            String message = switch (result) {
                case Success(var user) -> "Found: " + user.name();
                case Failure(var err) -> "Error: " + err.getMessage();
            };

            assertEquals("Error: User not found: unknown", message);
        }

        @Test
        @DisplayName("Scenario: User lookup with fallback using Failure.recover()")
        void userLookupWithFallbackUsingRecover() {
            User guestUser = new User("guest", "Guest", "guest@example.com", 18);
            var result = findUserById("unknown");

            User user = switch (result) {
                case Success(var u) -> u;
                case Failure<User, UserNotFoundException> failure -> failure
                        .recover(e -> guestUser)
                        .unwrap();
            };

            assertEquals("Guest", user.name());
        }

        @Test
        @DisplayName("Scenario: Chained service calls with validation using filter")
        void chainedServiceCallsWithValidation() {
            var result = Vessel.<User, Exception>success(new User("1", "Alice", "alice@test.com", 25))
                    .filter(u -> u.age() >= 18, () -> new ValidationException("Must be adult"))
                    .filter(u -> u.email().contains("@"), () -> new ValidationException("Invalid email"))
                    .map(User::name)
                    .map(String::toUpperCase);

            switch (result) {
                case Success(var name) -> assertEquals("ALICE", name);
                case Failure(var err) -> fail("Expected Success");
            }
        }

        @Test
        @DisplayName("Scenario: Validation failure using fold")
        void validationFailureUsingFold() {
            var result = Vessel.<User, Exception>success(new User("1", "Young User", "young@test.com", 16))
                    .filter(u -> u.age() >= 18, () -> new ValidationException("Must be adult"))
                    .map(User::name);

            String message = result.fold(
                    name -> "Success: " + name,
                    Throwable::getMessage
            );

            assertEquals("Must be adult", message);
        }

        @Test
        @DisplayName("Scenario: Error logging with Failure.peekError()")
        void errorLoggingWithPeekError() {
            List<String> errorLog = new ArrayList<>();
            var result = findUserById("unknown");

            switch (result) {
                case Success(var user) -> fail("Expected Failure");
                case Failure<User, UserNotFoundException> failure -> {
                    failure.peekError(e -> errorLog.add("ERROR: " + e.getMessage()));
                    assertEquals(List.of("ERROR: User not found: unknown"), errorLog);
                }
            }
        }

        @Test
        @DisplayName("Scenario: Success logging with Success.peek()")
        void successLoggingWithPeek() {
            List<String> auditLog = new ArrayList<>();
            var result = findUserById("123");

            switch (result) {
                case Success<User, ?> success -> {
                    success.peek(u -> auditLog.add("User accessed: " + u.id()));
                    assertEquals(List.of("User accessed: 123"), auditLog);
                }
                case Failure(var err) -> fail("Expected Success");
            }
        }

        @Test
        @DisplayName("Scenario: Converting domain exception to API error using fold")
        void convertingDomainExceptionToApiError() {
            record ApiError(int code, String message) {
            }

            ApiError apiError = findUserById("unknown")
                    .map(User::name)
                    .fold(
                            name -> new ApiError(200, "Found: " + name),
                            e -> new ApiError(404, e.getMessage())
                    );

            assertEquals(404, apiError.code());
            assertEquals("User not found: unknown", apiError.message());
        }

        @Test
        @DisplayName("Scenario: Multiple recovery attempts using Failure.recoverWith()")
        void multipleRecoveryAttempts() {
            var result = findUserById("unknown");

            String userName = switch (result) {
                case Success(var user) -> user.name();
                case Failure<User, UserNotFoundException> failure -> {
                    var recovered = failure
                            .recoverWith(e -> findUserById("456"))
                            .recoverWith(e -> Vessel.success(new User("default", "Default User", "default@example.com", 18)));
                    yield switch (recovered) {
                        case Success(var user) -> user.name();
                        case Failure(var err) -> "No user";
                    };
                }
            };

            assertEquals("Default User", userName);
        }

        @Test
        @DisplayName("Scenario: Wrapping legacy exception-throwing code with lift")
        void wrappingLegacyCodeWithLift() {
            var parseResult = Vessel.lift(() -> Integer.parseInt("42"));

            int value = switch (parseResult) {
                case Success(var v) -> v;
                case Failure(var err) -> 0;
            };

            assertEquals(42, value);

            var failedParse = Vessel.lift(() -> Integer.parseInt("not-a-number"));

            int defaultValue = switch (failedParse) {
                case Success(var v) -> v;
                case Failure(var err) -> 0;
            };

            assertEquals(0, defaultValue);
        }

        @Test
        @DisplayName("Scenario: Transforming error types using mapError")
        void transformingErrorTypesForApiBoundaries() {
            class ApiException extends Exception {
                final int statusCode;

                ApiException(int statusCode, String message) {
                    super(message);
                    this.statusCode = statusCode;
                }
            }

            var result = findUserById("unknown")
                    .map(User::name)
                    .mapError(e -> new ApiException(404, "Resource not found"));

            switch (result) {
                case Success(var value) -> fail("Expected Failure");
                case Failure(var err) -> {
                    assertInstanceOf(ApiException.class, err);
                    ApiException apiEx = (ApiException) err;
                    assertEquals(404, apiEx.statusCode);
                    assertEquals("Resource not found", apiEx.getMessage());
                }
            }
        }

        @Test
        @DisplayName("Scenario: Building response with raise() for controllers")
        void buildingResponseWithRaise() {
            // Success case - use Success.raise()
            var successResult = findUserById("123");
            switch (successResult) {
                case Success<User, UserNotFoundException> success -> {
                    User user = success.get();
                    assertEquals("John Doe", user.name());
                }
                case Failure(var err) -> fail("Expected Success");
            }

            // Failure case - use Failure.raise()
            var failureResult = findUserById("unknown");
            switch (failureResult) {
                case Success(var user) -> fail("Expected Failure");
                case Failure<User, UserNotFoundException> failure -> {
                    assertThrows(UserNotFoundException.class, failure::raise);
                }
            }
        }

        @Test
        @DisplayName("Scenario: Complex data processing pipeline")
        void complexDataProcessingPipeline() {
            record Order(String id, String userId, double amount) {
            }
            record ProcessedOrder(String orderId, String userName, double finalAmount) {
            }

            Order order = new Order("ORD-001", "123", 100.0);
            var result = Vessel.<Order, Exception>success(order)
                    .filter(o -> o.amount() > 0, () -> new ValidationException("Order amount must be positive"))
                    .flatMap(o -> findUserById(o.userId())
                            .mapError(e -> e)
                            .map(user -> new ProcessedOrder(o.id(), user.name(), o.amount() * 1.1)));

            switch (result) {
                case Success(var processed) -> {
                    assertEquals("ORD-001", processed.orderId());
                    assertEquals("John Doe", processed.userName());
                    assertEquals(110.0, processed.finalAmount(), 0.01);
                }
                case Failure(var err) -> fail("Expected Success: " + err.getMessage());
            }
        }

        @Test
        @DisplayName("Scenario: Lazy default value computation using Failure.replaceGet()")
        void lazyDefaultValueComputation() {
            int[] computationCount = {0};

            // Failure case - expensive computation IS called
            var failureVessel = Vessel.<String, IOException>failure(new IOException());
            switch (failureVessel) {
                case Success(var value) -> fail("Expected Failure");
                case Failure<String, IOException> failure -> {
                    String result = failure.replaceGet(() -> {
                        computationCount[0]++;
                        return "expensive computation";
                    });
                    assertEquals("expensive computation", result);
                    assertEquals(1, computationCount[0]);
                }
            }
        }
    }
}
