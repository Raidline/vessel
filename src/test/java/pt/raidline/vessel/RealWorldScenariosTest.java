package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("Real-World Scenarios")
class RealWorldScenariosTest {

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
        var result = Vessel.success(new User("1", "Alice", "alice@test.com", 25))
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
        var result = Vessel.success(new User("1", "Young User", "young@test.com", 16))
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
                ApiException apiEx = err;
                assertEquals(404, apiEx.statusCode);
                assertEquals("Resource not found", apiEx.getMessage());
            }
        }
    }

    @Test
    @DisplayName("Scenario: Building response with raise() for controllers")
    void buildingResponseWithRaise() {
        // Success case - use Success.get()
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
