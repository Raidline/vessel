package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("traverse()")
class TraverseTest {

    @Test
    @DisplayName("should return Success with mapped list when all mappings succeed")
    void shouldReturnSuccessWhenAllMappingsSucceed() {
        List<String> ids = List.of("1", "2", "3");

        var result = Vessel.traverse(ids, id -> Vessel.success(Integer.parseInt(id)));

        switch (result) {
            case Success(var values) -> {
                assertEquals(3, values.size());
                assertEquals(1, values.get(0));
                assertEquals(2, values.get(1));
                assertEquals(3, values.get(2));
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when any mapping fails")
    void shouldReturnFailureWhenAnyMappingFails() {
        List<String> ids = List.of("1", "invalid", "3");

        var result = Vessel.traverse(
                ids,
                id -> Vessel.lift(() -> Integer.parseInt(id))
        );

        switch (Objects.requireNonNull(result)) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertInstanceOf(NumberFormatException.class, err);
        }
    }

    @Test
    @DisplayName("should return Failure with first error when multiple mappings fail")
    void shouldReturnFirstFailureWhenMultipleMappingsFail() {
        IOException firstException = new IOException("user 2 not found");
        IOException secondException = new IOException("user 3 not found");
        List<String> ids = List.of("1", "2", "3");

        var result = Vessel.traverse(
                ids,
                id -> {
                    if (id.equals("2")) return Vessel.failure(firstException);
                    if (id.equals("3")) return Vessel.failure(secondException);
                    return Vessel.success("User " + id);
                }
        );

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(firstException, err);
        }
    }

    @Test
    @DisplayName("should return Success with empty list when given empty list")
    void shouldReturnSuccessWithEmptyListWhenGivenEmptyList() {
        List<String> ids = List.of();

        var result = Vessel.traverse(ids, id -> Vessel.success(id.length()));

        switch (result) {
            case Success(var values) -> assertTrue(values.isEmpty());
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Success with single mapped element")
    void shouldReturnSuccessWithSingleMappedElement() {
        List<String> ids = List.of("42");

        var result = Vessel.traverse(ids, id -> Vessel.success(Integer.parseInt(id)));

        switch (result) {
            case Success(var values) -> {
                assertEquals(1, values.size());
                assertEquals(42, values.getFirst());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should work with complex types")
    void shouldWorkWithComplexTypes() {
        record User(String id, String name) {
        }

        List<String> userIds = List.of("101", "102", "103");

        var result = Vessel.traverse(
                userIds,
                id -> Vessel.success(new User(id, "User " + id))
        );

        switch (result) {
            case Success(var users) -> {
                assertEquals(3, users.size());
                assertEquals("101", users.get(0).id());
                assertEquals("User 101", users.get(0).name());
                assertEquals("102", users.get(1).id());
                assertEquals("103", users.get(2).id());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("real-world scenario: fetching users by IDs")
    void realWorldScenarioFetchingUsers() {
        record User(String id, String name) {
        }

        // Simulating a repository that can find users
        List<String> userIds = List.of("101", "102", "103");

        var result = Vessel.traverse(
                userIds,
                id -> {
                    // Simulate finding user - all exist
                    return Vessel.success(new User(id, "User " + id));
                }
        );

        switch (result) {
            case Success(var users) -> assertEquals(3, users.size());
            case Failure(var err) -> fail("Expected all users to be found");
        }
    }

    @Test
    @DisplayName("real-world scenario: fetching users with missing ID")
    void realWorldScenarioFetchingUsersWithMissingId() {
        record User(String id, String name) {
        }
        class UserNotFoundException extends Exception {
            UserNotFoundException(String msg) {
                super(msg);
            }
        }

        List<String> userIds = List.of("101", "102", "103");

        var result = Vessel.traverse(
                userIds,
                id -> {
                    // Simulate: user 102 doesn't exist
                    if (id.equals("102")) {
                        return Vessel.failure(new UserNotFoundException("User 102 not found"));
                    }
                    return Vessel.success(new User(id, "User " + id));
                }
        );

        switch (result) {
            case Success(var users) -> fail("Expected Failure because user 102 doesn't exist");
            case Failure(var err) -> assertEquals("User 102 not found", err.getMessage());
        }
    }

    @Test
    @DisplayName("should be equivalent to map + sequence")
    void shouldBeEquivalentToMapThenSequence() {
        List<String> ids = List.of("1", "2", "3");

        // Using traverse
        var traverseResult = Vessel.<String, Integer, RuntimeException>traverse(
                ids,
                id -> Vessel.success(Integer.parseInt(id))
        );

        // Using map + sequence (equivalent)
        List<Vessel<Integer, RuntimeException>> mapped = ids.stream()
                .map(id -> Vessel.<Integer, RuntimeException>success(Integer.parseInt(id)))
                .toList();
        var sequenceResult = Vessel.sequence(mapped);

        // Both should produce the same result
        switch (Objects.requireNonNull(traverseResult)) {
            case Success(var traverseValues) -> {
                switch (Objects.requireNonNull(sequenceResult)) {
                    case Success(var sequenceValues) -> assertEquals(traverseValues, sequenceValues);
                    case Failure(var err) -> fail("Expected sequence to succeed");
                }
            }
            case Failure(var err) -> fail("Expected traverse to succeed");
        }
    }
}
