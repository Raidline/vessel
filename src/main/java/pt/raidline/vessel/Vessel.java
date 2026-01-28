package pt.raidline.vessel;

import pt.raidline.vessel.exception.MergeZipFailureException;
import pt.raidline.vessel.exception.ValueNotPresentException;
import pt.raidline.vessel.lambdas.Throwing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * A container type that represents a computation that may either succeed with a value of type {@code V}
 * or fail with an exception of type {@code E}. Similar to Rust's {@code Result} type or Scala's {@code Either}.
 * <p>
 * {@code Vessel} is a sealed interface with two permitted implementations:
 * <ul>
 *   <li>{@link Success} - represents a successful computation containing a value</li>
 *   <li>{@link Failure} - represents a failed computation containing an exception</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Type-safe error handling without throwing exceptions</li>
 *   <li>Functional composition with {@link #map}, {@link #flatMap}, and {@link #fold}</li>
 *   <li>Pattern matching support with Java's sealed classes</li>
 *   <li>Combining operations with {@link #zip}, {@link #oneOf}, {@link #sequence}, and {@link #traverse}</li>
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Basic Usage</h3>
 * <pre>{@code
 * // Creating a Success
 * Vessel<String, IOException> success = Vessel.success("Hello");
 *
 * // Creating a Failure
 * Vessel<String, IOException> failure = Vessel.failure(new IOException("File not found"));
 *
 * // Wrapping exception-throwing code
 * Vessel<Integer, NumberFormatException> parsed = Vessel.lift(() -> Integer.parseInt("42"));
 * }</pre>
 *
 * <h3>Transformation and Chaining</h3>
 * <pre>{@code
 * Vessel<User, UserException> result = Vessel.lift(() -> repository.findById(id))
 *     .map(user -> user.withUpdatedName("New Name"))
 *     .filter(user -> user.isActive(), () -> new UserException("User is inactive"))
 *     .flatMap(user -> saveUser(user));
 * }</pre>
 *
 * <h3>Pattern Matching</h3>
 * <pre>{@code
 * String message = switch (result) {
 *     case Success(var user) -> "Found user: " + user.name();
 *     case Failure(var err) -> "Error: " + err.getMessage();
 * };
 * }</pre>
 *
 * <h3>Error Handling with fold()</h3>
 * <pre>{@code
 * ApiResponse response = userResult.fold(
 *     user -> new ApiResponse(200, user),
 *     error -> new ApiResponse(404, error.getMessage())
 * );
 * }</pre>
 *
 * <h3>Combining Multiple Vessels</h3>
 * <pre>{@code
 * // Combine two vessels into one
 * Vessel<FullName, Exception> fullName = Vessel.zip(
 *     getFirstName(),
 *     getLastName(),
 *     (first, last) -> new FullName(first, last)
 * );
 *
 * // Collect a list of vessels into a vessel of list
 * List<Vessel<Config, ConfigException>> configs = List.of(
 *     loadConfig("database.yaml"),
 *     loadConfig("api.yaml")
 * );
 * Vessel<List<Config>, ConfigException> allConfigs = Vessel.sequence(configs);
 *
 * // Transform a list with a function that returns Vessel
 * List<String> userIds = List.of("1", "2", "3");
 * Vessel<List<User>, UserException> users = Vessel.traverse(
 *     userIds,
 *     id -> repository.findById(id)
 * );
 * }</pre>
 *
 * <h3>Stream Integration</h3>
 * <pre>{@code
 * Vessel<List<Integer>, Exception> collected = Stream.of("1", "2", "3")
 *     .map(s -> Vessel.lift(() -> Integer.parseInt(s)))
 *     .collect(Vessel.collectFromStream());
 * }</pre>
 *
 * @param <V> the type of the success value
 * @param <E> the type of the error (must extend Exception)
 * @see Success
 * @see Failure
 */
public sealed interface Vessel<V, E extends Exception> permits Failure, Success {

    /**
     * Lifts a potentially throwing computation into a {@code Vessel}.
     * <p>
     * If the computation succeeds, returns a {@link Success} containing the result.
     * If the computation throws an exception, returns a {@link Failure} containing the exception.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<Integer, NumberFormatException> parsed = Vessel.lift(() -> Integer.parseInt("42"));
     * // Returns Success(42)
     *
     * Vessel<Integer, NumberFormatException> failed = Vessel.lift(() -> Integer.parseInt("not-a-number"));
     * // Returns Failure(NumberFormatException)
     * }</pre>
     *
     * @param throwing the computation that may throw an exception
     * @param <V>      the type of the success value
     * @param <E>      the type of the exception
     * @return a {@link Success} if the computation succeeds, or a {@link Failure} if it throws
     */
    static <V, E extends Exception> Vessel<V, E> lift(Throwing<V, E> throwing) {
        try {
            var v = throwing.get();
            return success(v);
        } catch (Exception ex) {
            return failure((E) ex);
        }
    }

    /**
     * Creates a {@link Success} containing the given value.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> vessel = Vessel.success("Hello, World!");
     * }</pre>
     *
     * @param value the success value (must not be null)
     * @param <V>   the type of the success value
     * @param <E>   the type of the error
     * @return a {@link Success} containing the value
     * @throws NullPointerException if value is null
     */
    static <V, E extends Exception> Vessel<V, E> success(V value) {
        Objects.requireNonNull(value, "You cannot pass a [null] value as a success");
        return new Success<>(value);
    }

    /**
     * Creates a {@link Failure} containing the given exception.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, IOException> vessel = Vessel.failure(new IOException("File not found"));
     * }</pre>
     *
     * @param err the error exception (must not be null)
     * @param <V> the type of the success value
     * @param <E> the type of the error
     * @return a {@link Failure} containing the exception
     * @throws NullPointerException if err is null
     */
    static <V, E extends Exception> Vessel<V, E> failure(E err) {
        Objects.requireNonNull(err, "You cannot pass a [null] value as an err");
        return new Failure<>(err);
    }

    /**
     * Combines two {@code Vessel} instances using a merge function.
     * <p>
     * If both are {@link Success}, applies the merger function to their values.
     * If either or both are {@link Failure}, returns a {@link Failure} with a
     * {@link MergeZipFailureException} containing the error(s).
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> firstName = Vessel.success("John");
     * Vessel<String, Exception> lastName = Vessel.success("Doe");
     *
     * Vessel<String, Exception> fullName = Vessel.zip(
     *     firstName,
     *     lastName,
     *     (first, last) -> first + " " + last
     * );
     * // Returns Success("John Doe")
     * }</pre>
     *
     * @param first  the first vessel
     * @param second the second vessel
     * @param merger the function to combine the two success values
     * @param <R>    the type of the merged result
     * @param <T>    the type of the second vessel's value
     * @param <V>    the type of the first vessel's value
     * @param <E>    the type of the error
     * @return a {@link Success} with the merged value, or a {@link Failure} with the error(s)
     * @throws NullPointerException if any argument is null
     */
    static <R, T, V, E extends Exception> Vessel<R, E> zip(Vessel<V, E> first,
                                                           Vessel<T, E> second,
                                                           BiFunction<V, T, R> merger) {

        Objects.requireNonNull(first);
        Objects.requireNonNull(second);
        Objects.requireNonNull(merger);

        if (first.isFailure() && second.isFailure()) {
            var f1 = (Failure<V, E>) first;
            var f2 = (Failure<T, E>) second;

            return new Failure<>((E) new MergeZipFailureException(
                    "Both of the vessels are failure types",
                    f1.ex(),
                    f2.ex()
            ));
        }

        if (first.isFailure()) {
            return (Vessel<R, E>) first.mapError(e -> new MergeZipFailureException("First vessel has failed",
                    e,
                    null));
        }

        if (second.isFailure()) {
            return (Vessel<R, E>) second.mapError(e -> new MergeZipFailureException("Second vessel has failed",
                    e,
                    null));
        }

        var s1 = (Success<V, E>) first;
        var s2 = (Success<T, E>) second;

        return lift(() -> merger.apply(s1.value(), s2.value()));
    }

    /**
     * Returns the first {@link Success} from two vessels, or a combined failure if both fail.
     * <p>
     * Useful for fallback scenarios where you want to try a primary source and fall back to a secondary.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<Config, Exception> primary = loadFromPrimarySource();
     * Vessel<Config, Exception> fallback = loadFromFallback();
     *
     * Vessel<Config, Exception> config = Vessel.oneOf(primary, fallback);
     * // Returns primary if successful, otherwise fallback if successful,
     * // otherwise Failure with both errors
     * }</pre>
     *
     * @param first  the primary vessel to try first
     * @param second the fallback vessel to use if first fails
     * @param <V>    the type of the success value
     * @param <E>    the type of the error
     * @return the first {@link Success}, or a {@link Failure} with combined errors
     * @throws NullPointerException if any argument is null
     */
    static <V, E extends Exception> Vessel<V, E> oneOf(Vessel<V, E> first, Vessel<V, E> second) {

        Objects.requireNonNull(first);
        Objects.requireNonNull(second);

        if (first.isFailure() && second.isFailure()) {
            var f1 = (Failure<V, E>) first;
            var f2 = (Failure<V, E>) second;

            return new Failure<>((E) new MergeZipFailureException(
                    "Both of the vessels are failure types",
                    f1.ex(),
                    f2.ex()
            ));
        }

        if (first.isFailure()) {
            return second;
        }

        return first;
    }

    /**
     * Converts a {@code List<Vessel<V, E>>} into a {@code Vessel<List<V>, E>}.
     * <p>
     * If all vessels are {@link Success}, returns a {@link Success} containing a list of all values.
     * If any vessel is a {@link Failure}, returns the first failure encountered.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * List<Vessel<Config, ConfigException>> configs = List.of(
     *     loadConfig("database.yaml"),
     *     loadConfig("api.yaml"),
     *     loadConfig("security.yaml")
     * );
     *
     * Vessel<List<Config>, ConfigException> allConfigs = Vessel.sequence(configs);
     *
     * allConfigs.fold(
     *     configs -> System.out.println("Loaded " + configs.size() + " configs"),
     *     error -> System.out.println("Failed to load: " + error.getMessage())
     * );
     * }</pre>
     *
     * @param items the list of vessels to sequence
     * @param <V>   the type of the success value
     * @param <E>   the type of the error
     * @return a {@link Success} containing all values, or the first {@link Failure}
     */
    static <V, E extends Exception> Vessel<List<V>, E> sequence(List<Vessel<V, E>> items) {
        List<V> accumulator = new ArrayList<>(items.size());

        for (Vessel<V, E> item : items) {
            if (item instanceof Success<V, E> s) {
                accumulator.add(s.get());
            }
            if (item instanceof Failure<V, E>(E ex)) {
                return failure(ex);
            }
        }

        return success(accumulator);
    }

    /**
     * Applies a function that returns a {@code Vessel} to each element in a list,
     * collecting the results into a single {@code Vessel<List<R>, E>}.
     * <p>
     * Similar to {@link #sequence}, but applies a transformation function to each element first.
     * Stops at the first failure encountered.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * List<String> userIds = List.of("101", "102", "103");
     *
     * Vessel<List<User>, UserException> users = Vessel.traverse(
     *     userIds,
     *     id -> repository.findById(id)
     * );
     *
     * // If all users are found: Success(List<User>)
     * // If any user is not found: Failure with the first error
     * }</pre>
     *
     * @param items  the list of items to traverse
     * @param mapper the function to apply to each item, returning a Vessel
     * @param <V>    the type of input items
     * @param <R>    the type of the mapped success value
     * @param <E>    the type of the error
     * @return a {@link Success} containing all mapped values, or the first {@link Failure}
     */
    static <V, R, E extends Exception> Vessel<List<R>, E> traverse(List<V> items,
                                                                   Function<V, Vessel<R, E>> mapper) {
        List<R> results = new ArrayList<>(items.size());

        for (V item : items) {
            Vessel<R, E> vessel = mapper.apply(item);

            if (vessel instanceof Success<R, E>(R value)) {
                results.add(value);
            } else if (vessel instanceof Failure<R, E>(E ex)) {
                return new Failure<>(ex);
            }
        }

        return new Success<>(results);
    }

    /**
     * Returns a {@link Collector} that collects a stream of {@code Vessel} instances
     * into a single {@code Vessel<List<V>, E>}.
     * <p>
     * If all vessels in the stream are {@link Success}, returns a {@link Success} containing
     * a list of all values. If any vessel is a {@link Failure}, returns the first failure.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<List<Integer>, Exception> result = Stream.of("1", "2", "3", "4")
     *     .map(s -> Vessel.lift(() -> Integer.parseInt(s)))
     *     .collect(Vessel.collectFromStream());
     * // Returns Success(List.of(1, 2, 3, 4))
     *
     * Vessel<List<Integer>, Exception> withError = Stream.of("1", "invalid", "3")
     *     .map(s -> Vessel.lift(() -> Integer.parseInt(s)))
     *     .collect(Vessel.collectFromStream());
     * // Returns Failure(NumberFormatException)
     * }</pre>
     *
     * @param <V> the type of the success value
     * @param <E> the type of the error
     * @return a Collector that produces a {@code Vessel<List<V>, E>}
     */
    static <V, E extends Exception> Collector<Vessel<V, E>, ?, Vessel<List<V>, E>> collectFromStream() {

        class Acc {
            Vessel<List<V>, E> result = new Success<>(new ArrayList<>());

            void accumulate(Vessel<V, E> next) {
                if (result instanceof Success<List<V>, E>(List<V> items)) {
                    if (next instanceof Success<V, E>(V value)) {
                        items.add(value);
                    } else if (next instanceof Failure<V, E>(E ex)) {
                        result = new Failure<>(ex);
                    }
                }
            }

            Acc combine(Acc other) {
                if (this.result instanceof Success<List<V>, E>(List<V> items) &&
                        other.result instanceof Success<List<V>, E>(List<V> value)) {
                    items.addAll(value);
                    return this;
                }
                return (this.result instanceof Failure) ? this : other;
            }
        }

        return Collector.of(
                Acc::new,
                Acc::accumulate,
                Acc::combine,
                acc -> acc.result,
                Collector.Characteristics.UNORDERED
        );
    }

    /**
     * Returns {@code true} if this is a {@link Success}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> vessel = Vessel.success("hello");
     * if (vessel.isSuccess()) {
     *     System.out.println("Operation succeeded!");
     * }
     * }</pre>
     *
     * @return {@code true} if this is a Success, {@code false} otherwise
     */
    default boolean isSuccess() {
        return this instanceof Success<V, E>;
    }

    /**
     * Returns {@code true} if this is a {@link Failure}.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> vessel = Vessel.failure(new IOException("error"));
     * if (vessel.isFailure()) {
     *     System.out.println("Operation failed!");
     * }
     * }</pre>
     *
     * @return {@code true} if this is a Failure, {@code false} otherwise
     */
    default boolean isFailure() {
        return this instanceof Failure<V, E>;
    }

    /**
     * Transforms the success value using the given function.
     * <p>
     * If this is a {@link Success}, applies the mapper function and returns a new {@link Success}.
     * If this is a {@link Failure}, returns the failure unchanged.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> vessel = Vessel.success("hello");
     * Vessel<Integer, Exception> length = vessel.map(String::length);
     * // Returns Success(5)
     *
     * Vessel<String, Exception> failure = Vessel.failure(new Exception("error"));
     * Vessel<Integer, Exception> stillFailure = failure.map(String::length);
     * // Returns Failure(Exception("error"))
     * }</pre>
     *
     * @param mapper the function to apply to the success value
     * @param <R>    the type of the mapped value
     * @return a new {@link Success} with the mapped value, or the original {@link Failure}
     */
    default <R> Vessel<R, E> map(Function<V, R> mapper) {
        if (this instanceof Success<V, E>(V value)) {
            return flatMap(__ -> lift(() -> mapper.apply(value)));
        }

        return (Vessel<R, E>) this;
    }

    /**
     * Transforms the error using the given function.
     * <p>
     * If this is a {@link Failure}, applies the mapper function and returns a new {@link Failure}.
     * If this is a {@link Success}, returns the success unchanged.
     * <p>
     * Useful for converting domain exceptions to API exceptions at service boundaries.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<User, UserNotFoundException> result = findUser("123");
     * Vessel<User, ApiException> apiResult = result.mapError(
     *     e -> new ApiException(404, e.getMessage())
     * );
     * }</pre>
     *
     * @param mapper the function to apply to the error
     * @param <T>    the type of the new error
     * @return a new {@link Failure} with the mapped error, or the original {@link Success}
     */
    default <T extends Exception> Vessel<V, T> mapError(Function<E, T> mapper) {
        if (this instanceof Failure<V, E>(E err)) {
            return new Failure<>(mapper.apply(err));
        }

        return (Vessel<V, T>) this;
    }

    /**
     * Chains another {@code Vessel}-returning operation.
     * <p>
     * If this is a {@link Success}, applies the mapper function which returns another Vessel.
     * If this is a {@link Failure}, returns the failure unchanged.
     * <p>
     * This is the monadic "bind" operation, allowing sequential composition of operations
     * that may fail.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<User, UserException> result = findUser("123")
     *     .flatMap(user -> validateUser(user))
     *     .flatMap(user -> saveUser(user));
     * }</pre>
     *
     * @param mapper the function to apply, returning a new Vessel
     * @param <R>    the type of the new success value
     * @param <T>    the type of the new error
     * @return the result of the mapper function, or the original {@link Failure}
     */
    default <R, T extends Exception> Vessel<R, T> flatMap(Function<? super V, ? extends Vessel<R, T>> mapper) {
        if (this instanceof Success<V, E>(V value)) {
            return mapper.apply(value);
        }

        return (Vessel<R, T>) this;
    }

    /**
     * Handles both success and failure cases, returning a single result.
     * <p>
     * This is a terminal operation that extracts a value from the Vessel by applying
     * one of two functions depending on whether it's a Success or Failure.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<User, UserException> userResult = findUser("123");
     *
     * String message = userResult.fold(
     *     user -> "Found user: " + user.name(),
     *     error -> "Error: " + error.getMessage()
     * );
     *
     * // Or for API responses:
     * ApiResponse response = userResult.fold(
     *     user -> new ApiResponse(200, user),
     *     error -> new ApiResponse(404, error.getMessage())
     * );
     * }</pre>
     *
     * @param onSuccess the function to apply if this is a Success
     * @param onError   the function to apply if this is a Failure
     * @param <R>       the type of the result
     * @return the result of applying the appropriate function
     */
    default <R> R fold(Function<V, R> onSuccess, Function<E, R> onError) {
        return switch (this) {
            case Success(var value) -> onSuccess.apply(value);
            case Failure(var err) -> onError.apply(err);
        };
    }

    /**
     * Filters the success value using a predicate, converting to a failure if the predicate fails.
     * <p>
     * If this is a {@link Success} and the predicate returns {@code true}, returns the success unchanged.
     * If this is a {@link Success} and the predicate returns {@code false}, returns a new {@link Failure}.
     * If this is a {@link Failure}, returns the failure unchanged.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<User, UserException> user = Vessel.success(new User("John", 16));
     *
     * Vessel<User, UserException> adultUser = user.filter(
     *     u -> u.age() >= 18,
     *     () -> new UserException("User must be an adult")
     * );
     * // Returns Failure(UserException("User must be an adult"))
     * }</pre>
     *
     * @param predicate the predicate to test the success value
     * @param throwable the supplier for the exception if the predicate fails
     * @param <T>       the type of the new error
     * @return this {@link Success} if predicate passes, or a new {@link Failure}
     */
    default <T extends Exception> Vessel<V, T> filter(Predicate<V> predicate, Supplier<T> throwable) {
        if (this instanceof Failure<V, E>) {
            return (Vessel<V, T>) this;
        }

        var success = (Success<V, E>) this;

        if (predicate.test(success.value())) {
            return (Vessel<V, T>) this;
        }

        return new Failure<>(throwable.get());
    }

    /**
     * Extracts the success value, throwing if this is a failure.
     * <p>
     * <b>Warning:</b> This is an unsafe operation that defeats the purpose of using Vessel
     * for type-safe error handling. Prefer using {@link #fold}, pattern matching, or
     * {@link Success#get()} after confirming this is a success.
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<String, Exception> vessel = Vessel.success("hello");
     * String value = vessel.unwrap(); // Returns "hello"
     *
     * Vessel<String, Exception> failure = Vessel.failure(new Exception("error"));
     * failure.unwrap(); // Throws ValueNotPresentException
     * }</pre>
     *
     * @return the success value
     * @throws ValueNotPresentException if this is a Failure
     */
    default V unwrap() {
        if (this instanceof Failure<V, E>) {
            throw new ValueNotPresentException("This Vessel is of type failure, you just disappointed a Rust dev " +
                    "somewhere");
        }

        return ((Success<V, E>) this).get();
    }

    /**
     * Recovers from a failure by applying a function that returns a new Vessel.
     * <p>
     * If this is a {@link Failure}, applies the recovery function.
     * If this is a {@link Success}, returns this unchanged (with type cast).
     *
     * <h4>Example</h4>
     * <pre>{@code
     * Vessel<User, UserException> primaryResult = findUser("123");
     *
     * Vessel<User, UserException> withFallback = primaryResult.recoverWith(
     *     error -> findUserInCache("123")
     * );
     *
     * // Or with a default value:
     * Vessel<User, UserException> withDefault = primaryResult.recoverWith(
     *     error -> Vessel.success(User.guest())
     * );
     * }</pre>
     *
     * @param recovered the function to apply to the error, returning a new Vessel
     * @param <T>       the type of the recovered value
     * @return the result of the recovery function if Failure, or this if Success
     * @throws NullPointerException if recovered is null
     */
    default <T> Vessel<T, E> recoverWith(Function<E, Vessel<T, E>> recovered) {
        Objects.requireNonNull(recovered);

        if (this instanceof Failure<V, E>(var ex)) {
            return recovered.apply(ex);
        }

        return (Vessel<T, E>) this;
    }
}
