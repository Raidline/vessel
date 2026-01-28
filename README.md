# 🏺 Vessel

**Vessel** is a lightweight, type-safe functional error-handling library for Modern Java (21+).

It replaces the "hidden trapdoor" of Runtime Exceptions with explicit, composable **Result types**, allowing you to
treat errors as first-class values. No more fragmented `try-catch` blocks—just smooth, declarative data pipelines.

---

## 🚀 Why Vessel?

In standard Java, an exception interrupts the flow and forces the stack to unwind. In functional programming, we use the
**Railway Oriented Programming** pattern.

- **Success Track:** Data flows through your transformations (`map`, `flatMap`).
- **Failure Track:** If an error occurs, the data is shunted to a failure track, and subsequent operations are safely
  bypassed until you are ready to handle the error.

## ✨ Key Features (Java 21 Powered)

- **Sealed Hierarchies:** Leverages Java 21 `sealed interfaces` for compile-time exhaustiveness.
- **Pattern Matching:** Integrates natively with Record patterns in `switch` expressions.
- **Zero Dependencies:** Pure Java, high performance, and tiny footprint.

---

## 🛠 Usage at a Glance

```java
// Lift risky code into a Vessel
Vessel<User, Err> user = Vessel.lift(() -> repository.find(id));

// Chain operations safely
Vessel<String, Err> result = user
        .filter(User::isActive, Err.USER_INACTIVE)
        .flatMap(this::getPreferences)
        .map(Prefs::getTheme);

// Pattern match the outcome
String theme = switch (result) {
    case Success(var val) -> val;
    case Failure(var err) -> "default-dark";
};
```

## Features

This roadmap outlines the evolution of the library from core types to advanced concurrency tools.

### Phase 1: The Foundation (MVP)

Focus: Establishing the Monadic core.

- [x] Core Types: Implement Vessel<V, E>
- [x] Functor Ops: Implement .map(Function<V, U>) to transform values.
- [x] Monad Ops: Implement .flatMap(Function<V, Vessel<U, E>>) for operation chaining.
- [x] Inspection: Add isSuccess() and isFailure() utility methods.

### Phase 2: The Bridge (Legacy Interop)

Focus: Making Vessel play nice with standard Java libraries.

- [x] Vessel.lift(): Static factory to catch Exception and wrap into a Failure.
- [x] Side-Effect Hooks: peek(Consumer) and peekError(Consumer) for logging/telemetry.

### Phase 3: The Transformer (Advanced Logic)

Focus: Domain-specific logic and error recovery.

- [x] mapError: Transform error types (e.g., SQLException → DatabaseError).
- [x] recover: Jump back from the Failure track using a fallback function.
- [x] filter: Convert a Success to a Failure based on a Predicate.

### Phase 4: The Multi-Track (Combining Results)

Focus: Coordinating multiple independent results.

- [x] zip: Combine two independent Vessels into a single result (e.g., merging two API calls).
- [x] oneOf: Choose one of two independent Vessels into a single result. (If both are success the first wins)
- [x] sequence: Transform List<Vessel<V, E>> into Vessel<List<V>, E>.
- [x] traverse: Map a list of items to Vessels and sequence them in a single pass.

### Phase 5: The Speedster (Async & Streams)

Focus: Performance and high-scale execution.

- [x] Stream Collector: A custom Collector to gather a stream of results into one Vessel.
- [ ] AsyncVessel: Create a specialized wrapper for CompletableFuture<Vessel<V, E>>.
- [ ] Concurrency: Implement race() (first result wins) and allSettled().
