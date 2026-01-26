# 🏺 Vessel

**Vessel** is a lightweight, type-safe functional error-handling library for Modern Java (21+). 

It replaces the "hidden trapdoor" of Runtime Exceptions with explicit, composable **Result types**, allowing you to treat errors as first-class values. No more fragmented `try-catch` blocks—just smooth, declarative data pipelines.

---

## 🚀 Why Vessel?

In standard Java, an exception interrupts the flow and forces the stack to unwind. In functional programming, we use the **Railway Oriented Programming** pattern. 

- **Success Track:** Data flows through your transformations (`map`, `flatMap`).
- **Failure Track:** If an error occurs, the data is shunted to a failure track, and subsequent operations are safely bypassed until you are ready to handle the error.



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
