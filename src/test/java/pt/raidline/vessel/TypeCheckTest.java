package pt.raidline.vessel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("isSuccess() / isFailure()")
class TypeCheckTest {

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
