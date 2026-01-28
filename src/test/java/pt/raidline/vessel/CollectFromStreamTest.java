package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("collectFromStream()")
class CollectFromStreamTest {

    @Test
    @DisplayName("should collect stream of Success vessels into Success with list of values")
    void shouldCollectSuccessVesselsIntoSuccessWithList() {
        Stream<Vessel<String, RuntimeException>> vessels = Stream.of(
                Vessel.success("first"),
                Vessel.success("second"),
                Vessel.success("third")
        );

        Vessel<List<String>, RuntimeException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> {
                assertEquals(3, values.size());
                assertEquals("first", values.get(0));
                assertEquals("second", values.get(1));
                assertEquals("third", values.get(2));
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when any vessel in stream is Failure")
    void shouldReturnFailureWhenAnyIsFailure() {
        IOException exception = new IOException("stream error");
        Stream<Vessel<String, IOException>> vessels = Stream.of(
                Vessel.success("first"),
                Vessel.failure(exception),
                Vessel.success("third")
        );

        Vessel<List<String>, IOException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should return Failure with first error when multiple failures in stream")
    void shouldReturnFirstFailureWhenMultipleFailures() {
        IOException firstException = new IOException("first error");
        IOException secondException = new IOException("second error");
        Stream<Vessel<String, IOException>> vessels = Stream.of(
                Vessel.success("first"),
                Vessel.failure(firstException),
                Vessel.failure(secondException)
        );

        Vessel<List<String>, IOException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(firstException, err);
        }
    }

    @Test
    @DisplayName("should return Success with empty list when stream is empty")
    void shouldReturnSuccessWithEmptyListWhenStreamIsEmpty() {
        Stream<Vessel<String, RuntimeException>> vessels = Stream.empty();

        Vessel<List<String>, RuntimeException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> assertTrue(values.isEmpty());
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Success with single element when stream has one Success")
    void shouldReturnSuccessWithSingleElement() {
        Stream<Vessel<Integer, RuntimeException>> vessels = Stream.of(
                Vessel.success(42)
        );

        Vessel<List<Integer>, RuntimeException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> {
                assertEquals(1, values.size());
                assertEquals(42, values.getFirst());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when stream has single Failure")
    void shouldReturnFailureWhenSingleFailure() {
        IOException exception = new IOException("only error");
        Stream<Vessel<String, IOException>> vessels = Stream.of(
                Vessel.failure(exception)
        );

        Vessel<List<String>, IOException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should work with complex types")
    void shouldWorkWithComplexTypes() {
        record User(String id, String name) {}

        Stream<Vessel<User, RuntimeException>> vessels = Stream.of(
                Vessel.success(new User("1", "Alice")),
                Vessel.success(new User("2", "Bob")),
                Vessel.success(new User("3", "Charlie"))
        );

        Vessel<List<User>, RuntimeException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var users) -> {
                assertEquals(3, users.size());
                assertEquals("Alice", users.get(0).name());
                assertEquals("Bob", users.get(1).name());
                assertEquals("Charlie", users.get(2).name());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should work with mapped stream")
    void shouldWorkWithMappedStream() {
        List<String> ids = List.of("1", "2", "3");

        Vessel<List<Integer>, RuntimeException> result = ids.stream()
                .map(id -> Vessel.<Integer, RuntimeException>success(Integer.parseInt(id)))
                .collect(Vessel.collectFromStream());

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
    @DisplayName("should work with filtered stream")
    void shouldWorkWithFilteredStream() {
        List<Vessel<Integer, RuntimeException>> vesselList = List.of(
                Vessel.success(1),
                Vessel.success(2),
                Vessel.success(3),
                Vessel.success(4),
                Vessel.success(5)
        );

        Stream<Vessel<Integer, RuntimeException>> vessels = vesselList.stream()
                .filter(v -> v.fold(val -> val % 2 == 0, err -> false));

        Vessel<List<Integer>, RuntimeException> result = vessels.collect(Vessel.collectFromStream());

        switch (result) {
            case Success(var values) -> {
                assertEquals(2, values.size());
                assertEquals(2, values.get(0));
                assertEquals(4, values.get(1));
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("real-world scenario: collecting results from parallel operations")
    void realWorldScenarioCollectingParallelResults() {
        record ProcessingResult(String taskId, String status) {}

        Stream<Vessel<ProcessingResult, RuntimeException>> results = Stream.of(
                Vessel.success(new ProcessingResult("task-1", "completed")),
                Vessel.success(new ProcessingResult("task-2", "completed")),
                Vessel.success(new ProcessingResult("task-3", "completed"))
        );

        Vessel<List<ProcessingResult>, RuntimeException> collected = results.collect(Vessel.collectFromStream());

        switch (collected) {
            case Success(var allResults) -> {
                assertEquals(3, allResults.size());
                assertTrue(allResults.stream().allMatch(r -> "completed".equals(r.status())));
            }
            case Failure(var err) -> fail("Expected all tasks to complete successfully");
        }
    }

    @Test
    @DisplayName("real-world scenario: collecting with one failed operation")
    void realWorldScenarioCollectingWithFailure() {
        record ProcessingResult(String taskId, String status) {}
        class ProcessingException extends Exception {
            ProcessingException(String msg) { super(msg); }
        }

        Stream<Vessel<ProcessingResult, ProcessingException>> results = Stream.of(
                Vessel.success(new ProcessingResult("task-1", "completed")),
                Vessel.failure(new ProcessingException("Task 2 failed: timeout")),
                Vessel.success(new ProcessingResult("task-3", "completed"))
        );

        Vessel<List<ProcessingResult>, ProcessingException> collected = results.collect(Vessel.collectFromStream());

        switch (collected) {
            case Success(var allResults) -> fail("Expected Failure due to task-2");
            case Failure(var err) -> assertEquals("Task 2 failed: timeout", err.getMessage());
        }
    }

    @Test
    @DisplayName("should be equivalent to sequence(stream.toList())")
    void shouldBeEquivalentToSequence() {
        List<Vessel<Integer, RuntimeException>> vesselList = List.of(
                Vessel.success(1),
                Vessel.success(2),
                Vessel.success(3)
        );

        // Using collectFromStream
        Vessel<List<Integer>, RuntimeException> collectedResult = vesselList.stream()
                .collect(Vessel.collectFromStream());

        // Using sequence
        Vessel<List<Integer>, RuntimeException> sequenceResult = Vessel.sequence(vesselList);

        // Both should produce the same result
        switch (collectedResult) {
            case Success(var collectedValues) -> {
                switch (sequenceResult) {
                    case Success(var sequenceValues) -> assertEquals(collectedValues, sequenceValues);
                    case Failure(var err) -> fail("Expected sequence to succeed");
                }
            }
            case Failure(var err) -> fail("Expected collectFromStream to succeed");
        }
    }
}
