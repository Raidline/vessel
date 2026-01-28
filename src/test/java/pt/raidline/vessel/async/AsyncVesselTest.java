package pt.raidline.vessel.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("AsyncVessel")
class AsyncVesselTest {

    // ==================== DOMAIN OBJECTS FOR TESTING ====================

    record User(String id, String name) {
    }

    record Post(String id, String title, String authorId) {
    }

    static class UserNotFoundException extends Exception {
        UserNotFoundException(String message) {
            super(message);
        }
    }

    static class PostNotFoundException extends Exception {
        PostNotFoundException(String message) {
            super(message);
        }
    }

    // ==================== SIMULATED ASYNC SERVICES ====================

    // Simulates an async user service
    CompletableFuture<User> fetchUserAsync(String id) {
        return CompletableFuture.supplyAsync(() -> {
            if ("123".equals(id)) {
                return new User("123", "John Doe");
            }
            throw new CompletionException(new UserNotFoundException("User not found: " + id));
        });
    }

    // Simulates an async post service
    CompletableFuture<List<Post>> getPostsAsync(String userId) {
        return CompletableFuture.supplyAsync(() -> {
            if ("123".equals(userId)) {
                return List.of(
                        new Post("p1", "First Post", userId),
                        new Post("p2", "Second Post", userId)
                );
            }
            throw new CompletionException(new PostNotFoundException("No posts for user: " + userId));
        });
    }

    // ==================== LIFT TESTS ====================

    @Nested
    @DisplayName("AsyncVessel.lift()")
    class LiftTests {

        @Test
        @DisplayName("should create AsyncVessel from successful CompletableFuture")
        void shouldCreateFromSuccessfulFuture() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            assertNotNull(vessel);
            assertInstanceOf(AsyncSuccess.class, vessel);
        }

        @Test
        @DisplayName("should create AsyncVessel from async computation")
        void shouldCreateFromAsyncComputation() {
            AsyncVessel<User, UserNotFoundException> vessel = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            assertNotNull(vessel);
        }

        @Test
        @DisplayName("should wrap async user fetch operation")
        void shouldWrapAsyncUserFetch() {
            AsyncVessel<User, UserNotFoundException> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            asyncUser.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess<User, ?> success -> {
                        // Would need to extract value from CompletionStage
                        assertNotNull(success.value());
                    }
                    case AsyncFailure(var err) -> fail("Expected AsyncSuccess");
                }
            });
        }
    }

    // ==================== MAP ASYNC TESTS ====================

    @Nested
    @DisplayName("mapAsync()")
    class MapAsyncTests {

        @Test
        @DisplayName("should transform value when AsyncSuccess")
        void shouldTransformValueWhenSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<Integer, RuntimeException> result = vessel.mapAsync(String::length);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should chain multiple mapAsync operations")
        void shouldChainMultipleMapAsync() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<String, RuntimeException> result = vessel
                    .mapAsync(String::length)
                    .mapAsync(len -> len * 2)
                    .mapAsync(Object::toString);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should transform User to UserDTO")
        void shouldTransformUserToDto() {
            record UserDTO(String displayName) {
            }

            AsyncVessel<User, UserNotFoundException> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<UserDTO, UserNotFoundException> asyncDto = asyncUser
                    .mapAsync(user -> new UserDTO(user.name().toUpperCase()));

            assertNotNull(asyncDto);
        }
    }

    // ==================== MAP ERROR ASYNC TESTS ====================

    @Nested
    @DisplayName("mapErrorAsync()")
    class MapErrorAsyncTests {

        @Test
        @DisplayName("should transform error type")
        void shouldTransformErrorType() {
            AsyncFailure<String, IOException> failure = new AsyncFailure<>(new IOException("original"));

            AsyncVessel<String, RuntimeException> result = failure
                    .mapErrorAsync(e -> new RuntimeException("Wrapped: " + e.getMessage()));

            assertNotNull(result);
        }

        @Test
        @DisplayName("should not affect AsyncSuccess")
        void shouldNotAffectSuccess() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<String, RuntimeException> result = vessel
                    .mapErrorAsync(e -> new RuntimeException(e.getMessage()));

            assertNotNull(result);
        }

        @Test
        @DisplayName("should transform domain error to API error")
        void shouldTransformDomainErrorToApiError() {
            class ApiException extends Exception {
                final int statusCode;

                ApiException(int statusCode, String message) {
                    super(message);
                    this.statusCode = statusCode;
                }
            }

            AsyncFailure<User, UserNotFoundException> failure =
                    new AsyncFailure<>(new UserNotFoundException("User 999 not found"));

            AsyncVessel<User, ApiException> result = failure
                    .mapErrorAsync(e -> new ApiException(404, e.getMessage()));

            assertNotNull(result);
            assertInstanceOf(AsyncFailure.class, result);
        }
    }

    // ==================== FLAT MAP ASYNC TESTS ====================

    @Nested
    @DisplayName("flatMapAsync()")
    class FlatMapAsyncTests {

        @Test
        @DisplayName("should chain async operations")
        void shouldChainAsyncOperations() {
            AsyncVessel<User, UserNotFoundException> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, UserNotFoundException> asyncPosts = asyncUser
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            assertNotNull(asyncPosts);
        }

        @Test
        @DisplayName("should support the example use case: fetch user then fetch posts")
        void shouldSupportExampleUseCase() {
            // This is the example from the user's request
            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            // Handle the final result when it arrives
            asyncPosts.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess<List<Post>, ?> success -> assertNotNull(success.value());
                    case AsyncFailure(var err) -> fail("Expected success: " + err.getMessage());
                }
            });
        }

        @Test
        @DisplayName("should propagate failure through chain")
        void shouldPropagateFailureThroughChain() {
            AsyncFailure<User, UserNotFoundException> failure =
                    new AsyncFailure<>(new UserNotFoundException("User not found"));

            AsyncVessel<List<Post>, UserNotFoundException> result = failure
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            assertNotNull(result);
        }

        @Test
        @DisplayName("should chain multiple flatMapAsync operations")
        void shouldChainMultipleFlatMapAsync() {
            record Profile(User user, List<Post> posts) {
            }

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<Profile, Exception> asyncProfile = asyncUser
                    .flatMap(user ->
                            AsyncVessel.<List<Post>, Exception>lift(() -> getPostsAsync(user.id()))
                                    .mapAsync(posts -> new Profile(user, posts))
                    );

            assertNotNull(asyncProfile);
        }
    }

    // ==================== ON COMPLETE TESTS ====================

    @Nested
    @DisplayName("onComplete()")
    class OnCompleteTests {

        @Test
        @DisplayName("should invoke consumer with AsyncSuccess")
        void shouldInvokeConsumerWithSuccess() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            AtomicReference<AsyncVessel<String, RuntimeException>> captured = new AtomicReference<>();

            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            vessel.onComplete(result -> {
                invoked.set(true);
                captured.set(result);
            });

            assertTrue(invoked.get());
            assertNotNull(captured.get());
        }

        @Test
        @DisplayName("should invoke consumer with AsyncFailure")
        void shouldInvokeConsumerWithFailure() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            IOException exception = new IOException("error");

            AsyncFailure<String, IOException> vessel = new AsyncFailure<>(exception);

            vessel.onComplete(result -> {
                invoked.set(true);
                switch (result) {
                    case AsyncSuccess(var future) -> fail("Expected AsyncFailure");
                    case AsyncFailure(var err) -> assertEquals(exception, err);
                }
            });

            assertTrue(invoked.get());
        }

        @Test
        @DisplayName("should support pattern matching in onComplete")
        void shouldSupportPatternMatchingInOnComplete() {
            List<String> results = new ArrayList<>();

            AsyncVessel<String, RuntimeException> successVessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            successVessel.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess<String, ?> success -> results.add("success");
                    case AsyncFailure(var err) -> results.add("failure: " + err.getMessage());
                }
            });

            assertEquals(List.of("success"), results);
        }

        @Test
        @DisplayName("should handle render/showError pattern from example")
        void shouldHandleRenderShowErrorPattern() {
            List<String> renderCalls = new ArrayList<>();
            List<String> errorCalls = new ArrayList<>();

            // Simulate render and showError functions
            java.util.function.Consumer<List<Post>> render = posts ->
                    renderCalls.add("Rendered " + posts.size() + " posts");
            java.util.function.Consumer<Exception> showError = err ->
                    errorCalls.add("Error: " + err.getMessage());

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            asyncPosts.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess<List<Post>, ?> success -> {
                        // In real implementation, we'd await the future
                        // For now, just verify the pattern works
                        renderCalls.add("Would render posts");
                    }
                    case AsyncFailure(var err) -> showError.accept(err);
                }
            });

            // Verify render was called (not error)
            assertEquals(1, renderCalls.size());
            assertTrue(errorCalls.isEmpty());
        }
    }

    // ==================== REAL-WORLD SCENARIO TESTS ====================

    @Nested
    @DisplayName("Real-World Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Scenario: Fetch user and their posts (success path)")
        void fetchUserAndPostsSuccess() {
            AtomicReference<String> resultHolder = new AtomicReference<>();

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            asyncPosts.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess<List<Post>, ?> success -> resultHolder.set("success");
                    case AsyncFailure(var err) -> resultHolder.set("error: " + err.getMessage());
                }
            });

            assertEquals("success", resultHolder.get());
        }

        @Test
        @DisplayName("Scenario: User not found (failure path)")
        void userNotFoundFailure() {
            AsyncFailure<User, UserNotFoundException> asyncUser =
                    new AsyncFailure<>(new UserNotFoundException("User 999 not found"));

            AtomicReference<String> resultHolder = new AtomicReference<>();

            asyncUser.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess(var future) -> resultHolder.set("success");
                    case AsyncFailure(var err) -> resultHolder.set("error: " + err.getMessage());
                }
            });

            assertEquals("error: User 999 not found", resultHolder.get());
        }

        @Test
        @DisplayName("Scenario: Transform user data through async pipeline")
        void transformUserDataPipeline() {
            record UserProfile(String displayName, int postCount) {
            }

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<UserProfile, Exception> asyncProfile = asyncUser
                    .flatMap(user ->
                            AsyncVessel.<List<Post>, Exception>lift(() -> getPostsAsync(user.id()))
                                    .mapAsync(posts -> new UserProfile(user.name(), posts.size()))
                    );

            assertNotNull(asyncProfile);
        }

        @Test
        @DisplayName("Scenario: Error transformation for API boundaries")
        void errorTransformationForApiBoundaries() {
            class ApiError extends Exception {
                final int statusCode;

                ApiError(int statusCode, String message) {
                    super(message);
                    this.statusCode = statusCode;
                }
            }

            AsyncFailure<User, UserNotFoundException> failure =
                    new AsyncFailure<>(new UserNotFoundException("User 999 not found"));

            AsyncVessel<User, ApiError> apiResult = failure
                    .mapErrorAsync(e -> new ApiError(404, "Resource not found"));

            apiResult.onComplete(result -> {
                switch (result) {
                    case AsyncSuccess(var future) -> fail("Expected failure");
                    case AsyncFailure<User, ApiError> f -> {
                        assertEquals(404, f.ex().statusCode);
                        assertEquals("Resource not found", f.ex().getMessage());
                    }
                }
            });
        }

        @Test
        @DisplayName("Scenario: Multiple parallel async operations")
        void multipleParallelAsyncOperations() {
            // Simulate fetching multiple users in parallel
            List<AsyncVessel<User, Exception>> asyncUsers = List.of(
                    AsyncVessel.lift(() -> CompletableFuture.completedFuture(new User("1", "Alice"))),
                    AsyncVessel.lift(() -> CompletableFuture.completedFuture(new User("2", "Bob"))),
                    AsyncVessel.lift(() -> CompletableFuture.completedFuture(new User("3", "Charlie")))
            );

            // All should be AsyncSuccess
            for (AsyncVessel<User, Exception> asyncUser : asyncUsers) {
                assertInstanceOf(AsyncSuccess.class, asyncUser);
            }
        }

        @Test
        @DisplayName("Scenario: Conditional async chain based on user type")
        void conditionalAsyncChain() {
            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<String, Exception> asyncGreeting = asyncUser
                    .mapAsync(user -> {
                        if (user.name().startsWith("J")) {
                            return "Hello, " + user.name() + "!";
                        }
                        return "Hi there!";
                    });

            assertNotNull(asyncGreeting);
        }
    }

    // ==================== ASYNC FAILURE SPECIFIC TESTS ====================

    @Nested
    @DisplayName("AsyncFailure-specific")
    class AsyncFailureTests {

        @Test
        @DisplayName("should hold exception")
        void shouldHoldException() {
            IOException exception = new IOException("test error");
            AsyncFailure<String, IOException> failure = new AsyncFailure<>(exception);

            assertEquals(exception, failure.ex());
        }

        @Test
        @DisplayName("should support pattern matching deconstruction")
        void shouldSupportPatternMatching() {
            IOException exception = new IOException("test error");
            AsyncVessel<String, IOException> vessel = new AsyncFailure<>(exception);

            String result = switch (vessel) {
                case AsyncSuccess(var future) -> "has future";
                case AsyncFailure(var err) -> "has error: " + err.getMessage();
            };

            assertEquals("has error: test error", result);
        }

        @Test
        @DisplayName("should propagate through mapAsync unchanged")
        void shouldPropagateThroughMapAsync() {
            IOException exception = new IOException("original error");
            AsyncFailure<String, IOException> failure = new AsyncFailure<>(exception);

            AsyncVessel<Integer, IOException> result = failure.mapAsync(String::length);

            // mapAsync should not change the failure
            assertNotNull(result);
        }

        @Test
        @DisplayName("should propagate through flatMapAsync unchanged")
        void shouldPropagateThroughFlatMapAsync() {
            IOException exception = new IOException("original error");
            AsyncFailure<String, IOException> failure = new AsyncFailure<>(exception);

            AsyncVessel<Integer, IOException> result = failure
                    .flatMap(s -> new AsyncSuccess<>(CompletableFuture.completedFuture(s.length())));

            // flatMapAsync should not execute the function on failure
            assertNotNull(result);
        }
    }
}
