package pt.raidline.vessel.async;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pt.raidline.vessel.Failure;
import pt.raidline.vessel.Success;
import pt.raidline.vessel.Vessel;
import pt.raidline.vessel.exception.AsyncUnwrapException;
import pt.raidline.vessel.exception.ValueNotPresentException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertInstanceOf(AsyncPending.class, vessel);
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
                    case Success<User, ?> success -> {
                        assertNotNull(success.value());
                        assertEquals("123", success.get().id);
                    }
                    case Failure(var err) -> fail("Expected AsyncPending");
                }
            });
        }
    }

    // ==================== MAP ASYNC TESTS ====================

    @Nested
    @DisplayName("mapAsync()")
    class MapAsyncTests {

        @Test
        @DisplayName("should transform value when AsyncPending")
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
        @DisplayName("should not affect AsyncPending")
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
                    case Success<List<Post>, ?> success -> assertNotNull(success.value());
                    case Failure(var err) -> fail("Expected success: " + err.getMessage());
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

    // ==================== FLAT MAP TESTS (AsyncVessel -> AsyncVessel) ====================

    @Nested
    @DisplayName("flatMap() - AsyncVessel to AsyncVessel")
    class FlatMapTests {

        @Test
        @DisplayName("should chain AsyncVessel returning function on success")
        void shouldChainAsyncVesselReturningFunction() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<Integer, RuntimeException> result = vessel
                    .flatMap(s -> new AsyncPending<>(CompletableFuture.completedFuture(s.length())));

            assertNotNull(result);
            assertInstanceOf(AsyncPending.class, result);
        }

        @Test
        @DisplayName("should return AsyncFailure when mapper returns AsyncFailure")
        void shouldReturnFailureWhenMapperReturnsFailure() {
            IOException exception = new IOException("mapped error");
            AsyncVessel<String, IOException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<Integer, IOException> result = vessel
                    .flatMap(s -> new AsyncFailure<>(exception));

            assertNotNull(result);
        }

        @Test
        @DisplayName("should not execute mapper when AsyncFailure")
        void shouldNotExecuteMapperWhenFailure() {
            AtomicBoolean mapperCalled = new AtomicBoolean(false);
            IOException exception = new IOException("original error");
            AsyncFailure<String, IOException> failure = new AsyncFailure<>(exception);

            AsyncVessel<Integer, IOException> result = failure
                    .flatMap(s -> {
                        mapperCalled.set(true);
                        return new AsyncPending<>(CompletableFuture.completedFuture(s.length()));
                    });

            assertNotNull(result);
            // Mapper should NOT be called on failure
            assertFalse(mapperCalled.get()); // Uncomment when implementation is complete
        }

        @Test
        @DisplayName("should chain user fetch to posts fetch using AsyncVessel")
        void shouldChainUserFetchToPostsFetch() {
            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMap(user -> AsyncVessel.lift(() -> getPostsAsync(user.id())));

            assertNotNull(asyncPosts);
        }

        @Test
        @DisplayName("should support nested flatMap chains")
        void shouldSupportNestedFlatMapChains() {
            record UserWithPosts(User user, List<Post> posts) {
            }

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<UserWithPosts, Exception> result = asyncUser
                    .flatMap(user ->
                            AsyncVessel.<List<Post>, Exception>lift(() -> getPostsAsync(user.id()))
                                    .flatMap(posts ->
                                            new AsyncPending<>(CompletableFuture.completedFuture(new UserWithPosts(user, posts)))
                                    )
                    );

            assertNotNull(result);
        }

        @Test
        @DisplayName("should propagate original failure through flatMap chain")
        void shouldPropagateOriginalFailure() {
            UserNotFoundException originalError = new UserNotFoundException("User 999 not found");
            AsyncFailure<User, UserNotFoundException> failure = new AsyncFailure<>(originalError);

            AsyncVessel<List<Post>, UserNotFoundException> result = failure
                    .flatMap(user -> AsyncVessel.lift(() -> getPostsAsync(user.id())));

            assertNotNull(result);
            assertInstanceOf(AsyncFailure.class, result);

            if (result instanceof AsyncFailure<List<Post>, UserNotFoundException> f) {
                assertEquals(originalError, f.ex());
            }
        }

        @Test
        @DisplayName("should allow switching from success to failure in flatMap")
        void shouldAllowSwitchingToFailure() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("invalid")
            );

            IOException validationError = new IOException("Validation failed");

            AsyncVessel<Integer, IOException> result = vessel
                    .flatMap(s -> {
                        if ("invalid".equals(s)) {
                            return new AsyncFailure<>(validationError);
                        }
                        return new AsyncPending<>(CompletableFuture.completedFuture(s.length()));
                    });

            assertNotNull(result);
        }

        @Test
        @DisplayName("should chain with mapAsync after flatMap")
        void shouldChainWithMapAsyncAfterFlatMap() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<String, Exception> result = vessel
                    .flatMap(s -> new AsyncPending<>(CompletableFuture.completedFuture(s.length())))
                    .mapAsync(len -> "Length: " + len);

            assertNotNull(result);
        }

        @Test
        @DisplayName("should chain flatMap after mapAsync")
        void shouldChainFlatMapAfterMapAsync() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<String, RuntimeException> result = vessel
                    .mapAsync(String::toUpperCase)
                    .flatMap(s -> new AsyncPending<>(CompletableFuture.completedFuture(s + "!")));

            assertNotNull(result);
        }

        @Test
        @DisplayName("should handle complex pipeline with multiple flatMaps and mapAsyncs")
        void shouldHandleComplexPipeline() {
            record ProcessedData(String original, int length, String transformed) {
            }

            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("hello")
            );

            AsyncVessel<ProcessedData, Exception> result = vessel
                    .mapAsync(String::toUpperCase)
                    .flatMap(upper ->
                            new AsyncPending<>(CompletableFuture.completedFuture(upper.length()))
                                    .mapAsync(len -> new ProcessedData("hello", len, upper))
                    );

            assertNotNull(result);
            ProcessedData unwrap = result.unwrap();
            assertEquals("hello", unwrap.original);
            assertEquals("hello".length(), unwrap.length);
            assertEquals("HELLO", unwrap.transformed);
        }

        @Test
        @DisplayName("real-world: sequential async API calls")
        void realWorldSequentialApiCalls() {
            record AuthToken(String token) {
            }
            record ApiResponse(String data) {
            }

            // Simulate: authenticate -> fetch data with token
            AsyncVessel<AuthToken, Exception> asyncAuth = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture(new AuthToken("abc123"))
            );

            AsyncVessel<ApiResponse, Exception> asyncData = asyncAuth
                    .flatMap(token -> AsyncVessel.lift(
                            () -> CompletableFuture.completedFuture(new ApiResponse("data for " + token.token()))
                    ));

            assertNotNull(asyncData);
        }

        @Test
        @DisplayName("real-world: database transaction simulation")
        void realWorldDatabaseTransaction() {
            record Connection(String id) {
            }
            record QueryResult(List<String> rows) {
            }

            // Simulate: get connection -> execute query
            AsyncVessel<Connection, Exception> asyncConnection = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture(new Connection("conn-1"))
            );

            AsyncVessel<QueryResult, Exception> asyncQuery = asyncConnection
                    .flatMap(conn -> AsyncVessel.lift(
                            () -> CompletableFuture.completedFuture(
                                    new QueryResult(List.of("row1", "row2", "row3"))
                            )
                    ));

            asyncQuery.onComplete(result -> {
                switch (result) {
                    case Success<QueryResult, ?> success -> assertNotNull(success.value());
                    case Failure(var err) -> fail("Expected success");
                }
            });
        }

        @Test
        @DisplayName("real-world: file processing pipeline")
        void realWorldFileProcessingPipeline() {
            record FileContent(String content) {
            }
            record ParsedData(List<String> lines) {
            }
            record ProcessedResult(int lineCount, int charCount) {
            }

            // Simulate: read file -> parse -> process
            AsyncVessel<FileContent, Exception> asyncRead = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture(new FileContent("line1\nline2\nline3"))
            );

            AsyncVessel<ProcessedResult, Exception> asyncResult = asyncRead
                    .flatMap(content -> {
                        List<String> lines = List.of(content.content().split("\n"));
                        return new AsyncPending<>(CompletableFuture.completedFuture(new ParsedData(lines)));
                    })
                    .flatMap(parsed -> {
                        int lineCount = parsed.lines().size();
                        int charCount = parsed.lines().stream().mapToInt(String::length).sum();
                        return new AsyncPending<>(CompletableFuture.completedFuture(
                                new ProcessedResult(lineCount, charCount)
                        ));
                    });

            assertNotNull(asyncResult);
        }
    }

    // ==================== ON COMPLETE TESTS ====================

    @Nested
    @DisplayName("onComplete()")
    class OnCompleteTests {

        @Test
        @DisplayName("should invoke consumer with AsyncPending")
        void shouldInvokeConsumerWithSuccess() {
            AtomicBoolean invoked = new AtomicBoolean(false);
            AtomicReference<Vessel<String, RuntimeException>> captured = new AtomicReference<>();

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
                    case Success(var future) -> fail("Expected AsyncFailure");
                    case Failure(var err) -> assertEquals(exception, err);
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
                    case Success<String, ?> success -> results.add("success");
                    case Failure(var err) -> results.add("failure: " + err.getMessage());
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
            Consumer<List<Post>> render = posts ->
                    renderCalls.add("Rendered " + posts.size() + " posts");
            Consumer<Exception> showError = err ->
                    errorCalls.add("Error: " + err.getMessage());

            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> fetchUserAsync("123")
            );

            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMapAsync(user -> getPostsAsync(user.id()));

            asyncPosts.onComplete(result -> {
                switch (result) {
                    case Success<List<Post>, ?> success -> {
                        renderCalls.add("Would render posts");
                    }
                    case Failure(var err) -> showError.accept(err);
                }
            });

            // Verify render was called (not error)
            assertEquals(1, renderCalls.size());
            assertTrue(errorCalls.isEmpty());
        }

        @Test
        @DisplayName("should capture failure when lift succeeds but flatMapAsync fails")
        void shouldCaptureFailureWhenFlatMapAsyncFails() {
            AtomicBoolean failureCalled = new AtomicBoolean(false);
            AtomicReference<Exception> capturedError = new AtomicReference<>();
            PostNotFoundException expectedException = new PostNotFoundException("No posts found for user");

            // Start with a successful lift
            AsyncVessel<User, Exception> asyncUser = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture(new User("999", "Unknown User"))
            );

            // Chain to a flatMapAsync that fails
            AsyncVessel<List<Post>, Exception> asyncPosts = asyncUser
                    .flatMapAsync(user -> {
                        // This CompletableFuture will complete exceptionally
                        CompletableFuture<List<Post>> failingFuture = new CompletableFuture<>();
                        failingFuture.completeExceptionally(expectedException);
                        return failingFuture;
                    });

            // onComplete should receive the failure
            asyncPosts.onComplete(result -> {
                switch (result) {
                    case Success(var posts) -> fail("Expected Failure but got Success");
                    case Failure(var err) -> {
                        failureCalled.set(true);
                        capturedError.set(err);
                    }
                }
            });

            // Assert that failure was called and exception is what we expected
            assertTrue(failureCalled.get(), "Failure callback should have been invoked");
            assertNotNull(capturedError.get(), "Captured error should not be null");
            assertEquals(expectedException, capturedError.get(), "Captured error should be the expected exception");
            assertEquals("No posts found for user", capturedError.get().getMessage());
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
                    case Success<List<Post>, ?> success -> resultHolder.set("success");
                    case Failure(var err) -> resultHolder.set("error: " + err.getMessage());
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
                    case Success(var future) -> resultHolder.set("success");
                    case Failure(var err) -> resultHolder.set("error: " + err.getMessage());
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
                    case Success(var future) -> fail("Expected failure");
                    case Failure<User, ApiError> f -> {
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

            // All should be AsyncPending
            for (AsyncVessel<User, Exception> asyncUser : asyncUsers) {
                assertInstanceOf(AsyncPending.class, asyncUser);
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
                case AsyncPending(var future) -> "has future";
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
                    .flatMap(s -> new AsyncPending<>(CompletableFuture.completedFuture(s.length())));

            // flatMapAsync should not execute the function on failure
            assertNotNull(result);
        }
    }

    // ==================== EDGE CASES AND POTENTIAL ISSUES ====================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("should handle CompletionException wrapper in onComplete")
        void shouldHandleCompletionExceptionWrapper() {
            AtomicReference<Exception> capturedError = new AtomicReference<>();
            IOException originalException = new IOException("original cause");

            // Create a future that fails with CompletionException wrapping the real cause
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() -> {
                CompletableFuture<String> future = new CompletableFuture<>();
                future.completeExceptionally(new CompletionException(originalException));
                return future;
            });

            vessel.onComplete(result -> {
                switch (result) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> capturedError.set(err);
                }
            });

            assertNotNull(capturedError.get());
            // Should unwrap CompletionException to get the original cause
            assertEquals(originalException, capturedError.get());
        }

        @Test
        @DisplayName("should handle delayed failure in flatMapAsync chain")
        void shouldHandleDelayedFailureInFlatMapAsyncChain() throws InterruptedException {
            AtomicBoolean failureCalled = new AtomicBoolean(false);
            AtomicReference<Exception> capturedError = new AtomicReference<>();
            RuntimeException expectedException = new RuntimeException("delayed failure");

            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("start")
            );

            AsyncVessel<String, Exception> result = vessel
                    .flatMapAsync(s -> CompletableFuture.supplyAsync(() -> {
                        // Simulate some delay
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        throw expectedException;
                    }));

            result.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> {
                        failureCalled.set(true);
                        capturedError.set(err);
                    }
                }
            });

            // Wait for async completion
            Thread.sleep(100);

            assertTrue(failureCalled.get(), "Failure should have been called");
            assertNotNull(capturedError.get());
        }

        @Test
        @DisplayName("should handle exception thrown directly in flatMapAsync mapper")
        void shouldHandleExceptionInFlatMapAsyncMapper() {
            AtomicReference<Exception> capturedError = new AtomicReference<>();
            RuntimeException mapperException = new RuntimeException("mapper threw");

            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("start")
            );

            AsyncVessel<String, Exception> result = vessel
                    .flatMapAsync(s -> {
                        throw mapperException;  // Mapper throws instead of returning future
                    });

            result.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> capturedError.set(err);
                }
            });

            assertNotNull(capturedError.get());
        }

        @Test
        @DisplayName("should handle multiple chained operations with failure in middle")
        void shouldHandleFailureInMiddleOfChain() {
            AtomicReference<String> resultHolder = new AtomicReference<>();
            RuntimeException middleException = new RuntimeException("middle failed");

            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture("start")
            );

            AsyncVessel<String, Exception> result = vessel
                    .mapAsync(String::toUpperCase)
                    .flatMapAsync(s -> CompletableFuture.failedFuture(middleException))
                    .mapAsync(s -> s + " - should not reach");

            result.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> resultHolder.set("success: " + value);
                    case Failure(var err) -> resultHolder.set("failure: " + err.getMessage());
                }
            });

            assertEquals("failure: middle failed", resultHolder.get());
        }

        @Test
        @DisplayName("should handle already completed future in lift")
        void shouldHandleAlreadyCompletedFuture() {
            AtomicReference<String> resultHolder = new AtomicReference<>();

            // Already completed future
            CompletableFuture<String> completed = CompletableFuture.completedFuture("already done");

            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() -> completed);

            vessel.onComplete(r -> {
                switch (r) {
                    case Success(var s) -> resultHolder.set(s);
                    case Failure(var err) -> resultHolder.set("error");
                }
            });

            assertEquals("already done", resultHolder.get());
        }

        @Test
        @DisplayName("should handle already failed future in lift")
        void shouldHandleAlreadyFailedFuture() {
            AtomicReference<Exception> capturedError = new AtomicReference<>();
            IOException preFailure = new IOException("pre-failed");

            // Already failed future
            CompletableFuture<String> failed = CompletableFuture.failedFuture(preFailure);

            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() -> failed);

            vessel.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> capturedError.set(err);
                }
            });

            assertEquals(preFailure, capturedError.get());
        }

        @Test
        @DisplayName("should preserve error type through mapAsync on AsyncFailure")
        void shouldPreserveErrorTypeThroughMapAsync() {
            UserNotFoundException originalError = new UserNotFoundException("user 123 not found");
            AsyncFailure<User, UserNotFoundException> failure = new AsyncFailure<>(originalError);

            AsyncVessel<String, UserNotFoundException> result = failure.mapAsync(u -> u.name());

            assertInstanceOf(AsyncFailure.class, result);
            if (result instanceof AsyncFailure<String, UserNotFoundException> f) {
                assertEquals(originalError, f.ex());
                assertEquals("user 123 not found", f.ex().getMessage());
            }
        }

        @Test
        @DisplayName("should work with long async chain without stack overflow")
        void shouldWorkWithLongAsyncChain() {
            AsyncVessel<Integer, RuntimeException> vessel = AsyncVessel.lift(
                    () -> CompletableFuture.completedFuture(0)
            );

            // Chain 100 operations
            for (int i = 0; i < 100; i++) {
                vessel = vessel.mapAsync(n -> n + 1);
            }

            AtomicReference<Integer> resultHolder = new AtomicReference<>();
            vessel.onComplete(r -> {
                switch (r) {
                    case Success(var s) -> resultHolder.set(s);
                    case Failure(var err) -> fail("Expected Success");
                }
            });

            assertEquals(100, resultHolder.get());
        }

        @Test
        @DisplayName("flatMap should not call mapper when AsyncFailure")
        void flatMapShouldNotCallMapperOnFailure() {
            AtomicBoolean mapperWasCalled = new AtomicBoolean(false);
            IOException error = new IOException("initial failure");

            AsyncFailure<String, IOException> failure = new AsyncFailure<>(error);

            AsyncVessel<Integer, IOException> result = failure.flatMap(s -> {
                mapperWasCalled.set(true);
                return new AsyncPending<>(CompletableFuture.completedFuture(s.length()));
            });

            assertFalse(mapperWasCalled.get(), "Mapper should not be called on AsyncFailure");
            assertInstanceOf(AsyncFailure.class, result);
        }
    }

    // ==================== STATIC FACTORY METHODS TESTS ====================

    @Nested
    @DisplayName("AsyncVessel.success()")
    class SuccessFactoryTests {

        @Test
        @DisplayName("should create AsyncPending with the given value")
        void shouldCreateAsyncPendingWithValue() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertInstanceOf(AsyncPending.class, vessel);
            assertTrue(vessel.isPending());
            assertFalse(vessel.isFailure());
        }

        @Test
        @DisplayName("should allow unwrap to return the value immediately")
        void shouldAllowUnwrapToReturnValue() {
            AsyncVessel<Integer, RuntimeException> vessel = AsyncVessel.success(42);

            assertEquals(42, vessel.unwrap());
        }

        @Test
        @DisplayName("should work with null value")
        void shouldWorkWithNullValue() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success(null);

            assertNull(vessel.unwrap());
        }

        @Test
        @DisplayName("should work with complex types")
        void shouldWorkWithComplexTypes() {
            User user = new User("123", "John");
            AsyncVessel<User, RuntimeException> vessel = AsyncVessel.success(user);

            assertEquals(user, vessel.unwrap());
        }
    }

    @Nested
    @DisplayName("AsyncVessel.failure()")
    class FailureFactoryTests {

        @Test
        @DisplayName("should create AsyncFailure with the given exception")
        void shouldCreateAsyncFailureWithException() {
            IOException exception = new IOException("error");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(exception);

            assertInstanceOf(AsyncFailure.class, vessel);
            assertTrue(vessel.isFailure());
            assertFalse(vessel.isPending());
        }

        @Test
        @DisplayName("should throw NullPointerException for null exception")
        void shouldThrowForNullException() {
            assertThrows(NullPointerException.class, () ->
                    AsyncVessel.failure(null)
            );
        }

        @Test
        @DisplayName("should preserve exception in AsyncFailure")
        void shouldPreserveException() {
            UserNotFoundException exception = new UserNotFoundException("User not found");
            AsyncVessel<User, UserNotFoundException> vessel = AsyncVessel.failure(exception);

            vessel.onComplete(result -> {
                switch (result) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> assertEquals(exception, err);
                }
            });
        }
    }

    @Nested
    @DisplayName("AsyncVessel.fromStage()")
    class FromStageTests {

        @Test
        @DisplayName("should create AsyncPending from CompletionStage")
        void shouldCreateFromCompletionStage() {
            CompletableFuture<String> future = CompletableFuture.completedFuture("hello");
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.fromStage(future);

            assertInstanceOf(AsyncPending.class, vessel);
            assertEquals("hello", vessel.unwrap());
        }

        @Test
        @DisplayName("should throw NullPointerException for null stage")
        void shouldThrowForNullStage() {
            assertThrows(NullPointerException.class, () ->
                    AsyncVessel.fromStage(null)
            );
        }

        @Test
        @DisplayName("should work with already completed future")
        void shouldWorkWithCompletedFuture() {
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(100);
            AsyncVessel<Integer, RuntimeException> vessel = AsyncVessel.fromStage(future);

            assertEquals(100, vessel.unwrap());
        }

        @Test
        @DisplayName("should work with failed future")
        void shouldWorkWithFailedFuture() {
            IOException exception = new IOException("failed");
            CompletableFuture<String> future = CompletableFuture.failedFuture(exception);
            AsyncVessel<String, Exception> vessel = AsyncVessel.fromStage(future);

            AtomicReference<Exception> captured = new AtomicReference<>();
            vessel.onComplete(result -> {
                switch (result) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> captured.set(err);
                }
            });

            assertEquals(exception, captured.get());
        }
    }

    // ==================== RECOVERY METHODS TESTS ====================

    @Nested
    @DisplayName("recover()")
    class RecoverTests {

        @Test
        @DisplayName("should return recovered value on AsyncFailure")
        void shouldReturnRecoveredValueOnFailure() {
            IOException exception = new IOException("error");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(exception);

            AsyncVessel<String, IOException> result = vessel.recover(e -> "recovered: " + e.getMessage());

            assertEquals("recovered: error", result.unwrap());
        }

        @Test
        @DisplayName("should not affect AsyncPending")
        void shouldNotAffectSuccess() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.success("original");

            AsyncVessel<String, IOException> result = vessel.recover(e -> "recovered");

            assertEquals("original", result.unwrap());
        }

        @Test
        @DisplayName("should throw NullPointerException for null recovery function")
        void shouldThrowForNullRecovery() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException());

            assertThrows(NullPointerException.class, () ->
                    vessel.recover(null)
            );
        }

        @Test
        @DisplayName("should recover from failed CompletionStage")
        void shouldRecoverFromFailedCompletionStage() throws InterruptedException {
            RuntimeException exception = new RuntimeException("async failed");
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.failedFuture(exception)
            );

            AsyncVessel<String, Exception> result = vessel.recover(e -> "fallback");

            // Wait for async completion
            Thread.sleep(50);
            assertEquals("fallback", result.unwrap());
        }

        @Test
        @DisplayName("should use error information in recovery")
        void shouldUseErrorInformationInRecovery() {
            UserNotFoundException exception = new UserNotFoundException("User 123 not found");
            AsyncVessel<User, UserNotFoundException> vessel = AsyncVessel.failure(exception);

            AsyncVessel<User, UserNotFoundException> result = vessel.recover(e -> new User("guest", "Guest User"));

            assertEquals("Guest User", result.unwrap().name());
        }
    }

    @Nested
    @DisplayName("recoverWith()")
    class RecoverWithTests {

        @Test
        @DisplayName("should return recovered AsyncVessel on AsyncFailure")
        void shouldReturnRecoveredVesselOnFailure() {
            IOException exception = new IOException("error");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(exception);

            AsyncVessel<String, IOException> result = vessel.recoverWith(e -> AsyncVessel.success("recovered"));

            assertEquals("recovered", result.unwrap());
        }

        @Test
        @DisplayName("should allow chaining to another failure")
        void shouldAllowChainingToAnotherFailure() {
            IOException original = new IOException("original");
            IOException secondary = new IOException("secondary");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(original);

            AsyncVessel<String, IOException> result = vessel.recoverWith(e -> AsyncVessel.failure(secondary));

            AtomicReference<Exception> captured = new AtomicReference<>();
            result.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> captured.set(err);
                }
            });

            assertEquals(secondary, captured.get());
        }

        @Test
        @DisplayName("should not affect AsyncPending")
        void shouldNotAffectSuccess() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.success("original");

            AsyncVessel<String, IOException> result = vessel.recoverWith(e -> AsyncVessel.success("recovered"));

            assertEquals("original", result.unwrap());
        }

        @Test
        @DisplayName("should throw NullPointerException for null recovery function")
        void shouldThrowForNullRecovery() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException());

            assertThrows(NullPointerException.class, () ->
                    vessel.recoverWith(null)
            );
        }

        @Test
        @DisplayName("should allow recovery with async operation")
        void shouldAllowRecoveryWithAsyncOperation() {
            IOException exception = new IOException("primary failed");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(exception);

            AsyncVessel<String, IOException> result = vessel.recoverWith(e ->
                    AsyncVessel.lift(() -> CompletableFuture.completedFuture("async fallback"))
            );

            assertEquals("async fallback", result.unwrap());
        }
    }

    // ==================== TIMEOUT TESTS ====================

    @Nested
    @DisplayName("withTimeout()")
    class WithTimeoutTests {

        @Test
        @DisplayName("should complete normally if within timeout")
        void shouldCompleteNormallyWithinTimeout() {
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                        return "done";
                    })
            );

            AsyncVessel<String, Exception> result = vessel.withTimeout(
                    Duration.ofSeconds(1),
                    () -> new Exception("timeout")
            );

            assertEquals("done", result.unwrap());
        }

        @Test
        @DisplayName("should fail with timeout error if exceeded")
        void shouldFailWithTimeoutErrorIfExceeded() throws InterruptedException {
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        return "done";
                    })
            );

            AsyncVessel<String, Exception> result = vessel.withTimeout(
                    Duration.ofMillis(50),
                    () -> new Exception("operation timed out")
            );

            AtomicReference<Exception> captured = new AtomicReference<>();
            result.onComplete(r -> {
                switch (r) {
                    case Success(var value) -> fail("Expected Failure");
                    case Failure(var err) -> captured.set(err);
                }
            });

            // Wait for timeout to occur
            Thread.sleep(200);

            assertNotNull(captured.get());
            assertEquals("operation timed out", captured.get().getMessage());
        }

        @Test
        @DisplayName("should not affect AsyncFailure")
        void shouldNotAffectFailure() {
            IOException exception = new IOException("already failed");
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(exception);

            AsyncVessel<String, IOException> result = vessel.withTimeout(
                    Duration.ofSeconds(1),
                    () -> new IOException("timeout")
            );

            assertInstanceOf(AsyncFailure.class, result);
            assertTrue(result.isFailure());
        }

        @Test
        @DisplayName("should throw NullPointerException for null timeout")
        void shouldThrowForNullTimeout() {
            AsyncVessel<String, Exception> vessel = AsyncVessel.success("hello");

            assertThrows(NullPointerException.class, () ->
                    vessel.withTimeout(null, () -> new Exception("timeout"))
            );
        }

        @Test
        @DisplayName("should throw NullPointerException for null timeoutError supplier")
        void shouldThrowForNullTimeoutErrorSupplier() {
            AsyncVessel<String, Exception> vessel = AsyncVessel.success("hello");

            assertThrows(NullPointerException.class, () ->
                    vessel.withTimeout(Duration.ofSeconds(1), null)
            );
        }
    }

    // ==================== UNWRAP TESTS ====================

    @Nested
    @DisplayName("unwrap()")
    class UnwrapTests {

        @Test
        @DisplayName("should return value on AsyncPending")
        void shouldReturnValueOnSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertEquals("hello", vessel.unwrap());
        }

        @Test
        @DisplayName("should throw ValueNotPresentException on AsyncFailure")
        void shouldThrowOnFailure() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException("error"));

            assertThrows(ValueNotPresentException.class, vessel::unwrap);
        }

        @Test
        @DisplayName("should block until future completes")
        void shouldBlockUntilComplete() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        return "delayed";
                    })
            );

            assertEquals("delayed", vessel.unwrap());
        }

        @Test
        @DisplayName("should throw AsyncUnwrapException when future fails")
        void shouldThrowAsyncUnwrapExceptionWhenFutureFails() {
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.failedFuture(new RuntimeException("async error"))
            );

            assertThrows(AsyncUnwrapException.class, vessel::unwrap);
        }
    }

    @Nested
    @DisplayName("unwrap(timeout, unit)")
    class UnwrapWithTimeoutTests {

        @Test
        @DisplayName("should return value within timeout")
        void shouldReturnValueWithinTimeout() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertEquals("hello", vessel.unwrap(1, TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("should throw ValueNotPresentException on AsyncFailure")
        void shouldThrowOnFailure() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException("error"));

            assertThrows(ValueNotPresentException.class, () ->
                    vessel.unwrap(1, TimeUnit.SECONDS)
            );
        }

        @Test
        @DisplayName("should throw AsyncUnwrapException on timeout")
        void shouldThrowOnTimeout() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        return "delayed";
                    })
            );

            assertThrows(AsyncUnwrapException.class, () ->
                    vessel.unwrap(50, TimeUnit.MILLISECONDS)
            );
        }

        @Test
        @DisplayName("should throw NullPointerException for null unit")
        void shouldThrowForNullUnit() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertThrows(NullPointerException.class, () ->
                    vessel.unwrap(1, null)
            );
        }
    }

    // ==================== TO VESSEL TESTS ====================

    @Nested
    @DisplayName("toVessel()")
    class ToVesselTests {

        @Test
        @DisplayName("should convert AsyncPending to Success")
        void shouldConvertAsyncPendingToSuccess() {
            AsyncVessel<String, RuntimeException> asyncVessel = AsyncVessel.success("hello");

            Vessel<String, RuntimeException> vessel = asyncVessel.toVessel();

            assertInstanceOf(Success.class, vessel);
            assertEquals("hello", ((Success<String, RuntimeException>) vessel).value());
        }

        @Test
        @DisplayName("should convert AsyncFailure to Failure")
        void shouldConvertAsyncFailureToFailure() {
            IOException exception = new IOException("error");
            AsyncVessel<String, IOException> asyncVessel = AsyncVessel.failure(exception);

            Vessel<String, IOException> vessel = asyncVessel.toVessel();

            assertInstanceOf(Failure.class, vessel);
            assertEquals(exception, ((Failure<String, IOException>) vessel).ex());
        }

        @Test
        @DisplayName("should convert failed CompletionStage to Failure")
        void shouldConvertFailedStageToFailure() {
            RuntimeException exception = new RuntimeException("async error");
            AsyncVessel<String, Exception> asyncVessel = AsyncVessel.lift(() ->
                    CompletableFuture.failedFuture(exception)
            );

            Vessel<String, Exception> vessel = asyncVessel.toVessel();

            assertInstanceOf(Failure.class, vessel);
        }

        @Test
        @DisplayName("should block until future completes")
        void shouldBlockUntilComplete() {
            AsyncVessel<String, RuntimeException> asyncVessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                        return "delayed";
                    })
            );

            Vessel<String, RuntimeException> vessel = asyncVessel.toVessel();

            assertInstanceOf(Success.class, vessel);
            assertEquals("delayed", ((Success<String, RuntimeException>) vessel).value());
        }
    }

    @Nested
    @DisplayName("toVessel(timeout, unit)")
    class ToVesselWithTimeoutTests {

        @Test
        @DisplayName("should convert AsyncPending to Success within timeout")
        void shouldConvertWithinTimeout() {
            AsyncVessel<String, RuntimeException> asyncVessel = AsyncVessel.success("hello");

            Vessel<String, RuntimeException> vessel = asyncVessel.toVessel(1, TimeUnit.SECONDS);

            assertInstanceOf(Success.class, vessel);
            assertEquals("hello", ((Success<String, RuntimeException>) vessel).value());
        }

        @Test
        @DisplayName("should convert AsyncFailure to Failure")
        void shouldConvertFailure() {
            IOException exception = new IOException("error");
            AsyncVessel<String, IOException> asyncVessel = AsyncVessel.failure(exception);

            Vessel<String, IOException> vessel = asyncVessel.toVessel(1, TimeUnit.SECONDS);

            assertInstanceOf(Failure.class, vessel);
        }

        @Test
        @DisplayName("should return Failure on timeout")
        void shouldReturnFailureOnTimeout() {
            AsyncVessel<String, Exception> asyncVessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        return "delayed";
                    })
            );

            Vessel<String, Exception> vessel = asyncVessel.toVessel(50, TimeUnit.MILLISECONDS);

            assertInstanceOf(Failure.class, vessel);
        }

        @Test
        @DisplayName("should throw NullPointerException for null unit")
        void shouldThrowForNullUnit() {
            AsyncVessel<String, RuntimeException> asyncVessel = AsyncVessel.success("hello");

            assertThrows(NullPointerException.class, () ->
                    asyncVessel.toVessel(1, null)
            );
        }
    }

    // ==================== UTILITY METHODS TESTS ====================

    @Nested
    @DisplayName("isFailure()")
    class IsFailureTests {

        @Test
        @DisplayName("should return true for AsyncFailure")
        void shouldReturnTrueForFailure() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException("error"));

            assertTrue(vessel.isFailure());
        }

        @Test
        @DisplayName("should return false for AsyncPending")
        void shouldReturnFalseForSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertFalse(vessel.isFailure());
        }

        @Test
        @DisplayName("should return false for AsyncPending from lift")
        void shouldReturnFalseForLiftedSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.completedFuture("hello")
            );

            assertFalse(vessel.isFailure());
        }

        @Test
        @DisplayName("should return false for AsyncPending even with failed future")
        void shouldReturnFalseForFailedFuture() {
            // Note: AsyncPending with a failed future is still not an AsyncFailure
            // The failure state is in the future, not in the AsyncVessel type
            AsyncVessel<String, Exception> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.failedFuture(new RuntimeException("error"))
            );

            assertFalse(vessel.isFailure()); // It's AsyncPending, not AsyncFailure
        }
    }

    @Nested
    @DisplayName("isPending()")
    class IsPendingTests {

        @Test
        @DisplayName("should return true for AsyncPending")
        void shouldReturnTrueForSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.success("hello");

            assertTrue(vessel.isPending());
        }

        @Test
        @DisplayName("should return true for AsyncPending from lift")
        void shouldReturnTrueForLiftedSuccess() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.completedFuture("hello")
            );

            assertTrue(vessel.isPending());
        }

        @Test
        @DisplayName("should return false for AsyncFailure")
        void shouldReturnFalseForFailure() {
            AsyncVessel<String, IOException> vessel = AsyncVessel.failure(new IOException("error"));

            assertFalse(vessel.isPending());
        }

        @Test
        @DisplayName("should return true for pending async operation")
        void shouldReturnTrueForPendingOperation() {
            AsyncVessel<String, RuntimeException> vessel = AsyncVessel.lift(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                        return "delayed";
                    })
            );

            assertTrue(vessel.isPending());
        }
    }
}
