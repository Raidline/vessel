package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("sequence()")
class SequenceTest {

    @Test
    @DisplayName("should return Success with list of values when all vessels are Success")
    void shouldReturnSuccessWhenAllAreSuccess() {
        List<Vessel<String, RuntimeException>> vessels = List.of(
                Vessel.success("first"),
                Vessel.success("second"),
                Vessel.success("third")
        );

        var result = Vessel.sequence(vessels);

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
    @DisplayName("should return Failure when any vessel is Failure")
    void shouldReturnFailureWhenAnyIsFailure() {
        IOException exception = new IOException("load failed");
        List<Vessel<String, IOException>> vessels = List.of(
                Vessel.success("first"),
                Vessel.failure(exception),
                Vessel.success("third")
        );

        var result = Vessel.sequence(vessels);

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should return Failure with first error when multiple vessels are Failure")
    void shouldReturnFirstFailureWhenMultipleAreFailure() {
        IOException firstException = new IOException("first failed");
        IOException secondException = new IOException("second failed");
        List<Vessel<String, IOException>> vessels = List.of(
                Vessel.success("first"),
                Vessel.failure(firstException),
                Vessel.failure(secondException)
        );

        var result = Vessel.sequence(vessels);

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(firstException, err);
        }
    }

    @Test
    @DisplayName("should return Success with empty list when given empty list")
    void shouldReturnSuccessWithEmptyListWhenGivenEmptyList() {
        List<Vessel<String, RuntimeException>> vessels = List.of();

        var result = Vessel.sequence(vessels);

        switch (result) {
            case Success(var values) -> assertTrue(values.isEmpty());
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Success with single element when given single Success")
    void shouldReturnSuccessWithSingleElement() {
        List<Vessel<Integer, RuntimeException>> vessels = List.of(
                Vessel.success(42)
        );

        var result = Vessel.sequence(vessels);

        switch (result) {
            case Success(var values) -> {
                assertEquals(1, values.size());
                assertEquals(42, values.get(0));
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("should return Failure when single vessel is Failure")
    void shouldReturnFailureWhenSingleIsFailure() {
        IOException exception = new IOException("failed");
        List<Vessel<String, IOException>> vessels = List.of(
                Vessel.failure(exception)
        );

        var result = Vessel.sequence(vessels);

        switch (result) {
            case Success(var values) -> fail("Expected Failure");
            case Failure(var err) -> assertEquals(exception, err);
        }
    }

    @Test
    @DisplayName("should work with complex types")
    void shouldWorkWithComplexTypes() {
        record Config(String name, String value) {
        }

        List<Vessel<Config, RuntimeException>> configs = List.of(
                Vessel.success(new Config("database", "localhost")),
                Vessel.success(new Config("port", "5432")),
                Vessel.success(new Config("timeout", "30"))
        );

        var result = Vessel.sequence(configs);

        switch (result) {
            case Success(var allConfigs) -> {
                assertEquals(3, allConfigs.size());
                assertEquals("database", allConfigs.get(0).name());
                assertEquals("port", allConfigs.get(1).name());
                assertEquals("timeout", allConfigs.get(2).name());
            }
            case Failure(var err) -> fail("Expected Success");
        }
    }

    @Test
    @DisplayName("real-world scenario: loading multiple configs")
    void realWorldScenarioLoadingConfigs() {
        record Config(String name) {
        }
        class ConfigException extends Exception {
            ConfigException(String msg) {
                super(msg);
            }
        }

        // Simulating loading multiple config files
        List<Vessel<Config, ConfigException>> configs = List.of(
                Vessel.success(new Config("database.yaml")),
                Vessel.success(new Config("api.yaml")),
                Vessel.success(new Config("security.yaml"))
        );

        var result = Vessel.sequence(configs);

        switch (result) {
            case Success(var allConfigs) -> assertEquals(3, allConfigs.size());
            case Failure(var err) -> fail("Expected all configs to load");
        }
    }
}
