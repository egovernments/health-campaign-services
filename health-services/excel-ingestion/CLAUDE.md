# Claude Development Guidelines for Excel Ingestion Service

## Dependency Injection
- **NEVER use @Autowired** for dependency injection
- **ALWAYS use constructor injection** instead
- This ensures immutable dependencies and better testability

### Example:
```java
// ❌ Don't do this
@Service
public class ExcelService {
    @Autowired
    private DataRepository repository;
}

// ✅ Do this
@Service
public class ExcelService {
    private final DataRepository repository;
    
    public ExcelService(DataRepository repository) {
        this.repository = repository;
    }
}
```

## String Constants
- **NEVER hardcode strings** that are used in multiple places
- **ALWAYS create constants** for reusable strings
- Place constants in appropriate constant classes or interfaces

### Example:
```java
// ❌ Don't do this
if (status.equals("COMPLETED")) {
    // logic
}
// Later in another file
return "COMPLETED";

// ✅ Do this
public class ProcessingConstants {
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
}

// Usage
if (status.equals(ProcessingConstants.STATUS_COMPLETED)) {
    // logic
}
```

## Code Design Principles

### Keep It Simple
- **ALWAYS write simple, readable code** - avoid over-engineering
- **Use standard design patterns** - don't invent complex custom solutions
- **Prefer clear, explicit code** over clever, compact code
- **Write code that any developer can understand** in 5 minutes

### Examples:
```java
// ❌ Don't do this (over-complicated)
public class ComplexProcessor<T extends Processable & Validatable> {
    private final Map<Class<? extends T>, Function<T, CompletableFuture<ProcessingResult<T>>>> processorStrategies;
    // ... complex generic chains
}

// ✅ Do this (simple and clear)
public class DataProcessor {
    private final ValidationService validationService;
    private final ProcessingService processingService;
    
    public ProcessingResult processData(DataInput input) {
        // Simple, linear processing
    }
}
```

## Performance & Time Complexity

### Fast is Priority
- **ALWAYS consider time complexity** when writing algorithms
- **Prefer O(n) over O(n²)** - avoid nested loops when possible
- **Use appropriate data structures** - HashMap for lookups, ArrayList for iteration
- **Keep it simple AND fast** - don't sacrifice simplicity for micro-optimizations

### Performance Guidelines:
```java
// ❌ Avoid nested loops - O(n²)
for (User user : users) {
    for (Role role : roles) {
        if (user.hasRole(role.getId())) {
            // process
        }
    }
}

// ✅ Use Map for fast lookups - O(n)
Map<String, Role> roleMap = roles.stream()
    .collect(Collectors.toMap(Role::getId, Function.identity()));
    
for (User user : users) {
    Role role = roleMap.get(user.getRoleId()); // O(1) lookup
    if (role != null) {
        // process
    }
}

// ✅ Use HashSet for fast contains checks - O(1)
Set<String> validStatuses = Set.of("ACTIVE", "PENDING", "COMPLETED");
if (validStatuses.contains(status)) {
    // process
}
```

### Data Structure Choices:
- **ArrayList**: For iteration, indexed access - O(1) get, O(n) search
- **HashMap**: For key-based lookups - O(1) average get/put
- **HashSet**: For membership testing - O(1) contains
- **LinkedList**: Rarely needed - only when frequent insert/delete in middle

## Code Quality Rules
1. **Dependency Injection**: Use constructor injection for all dependencies
2. **Constants**: Create constants for repeated string values  
3. **Simplicity**: Keep code simple, readable, and maintainable
4. **Performance**: Consider time complexity, prefer O(n) over O(n²)
5. **Standard Patterns**: Use well-known design patterns, avoid custom complex solutions
6. **Readability**: Write code that any developer can understand quickly
7. **Error Handling**: Ensure proper error handling and validation
8. **Localization**: Any prompt or error message originating from a sheet should be localizable.

## When Optimizing
- **Profile first** - measure before optimizing
- **Optimize hot paths** - focus on code that runs frequently
- **Simple solutions first** - try simple approaches before complex ones
- **Readability wins** - don't sacrifice readability for small performance gains
