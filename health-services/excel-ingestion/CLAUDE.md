# Claude Development Guidelines for Excel Ingestion Service

## 🚨 TOP PRIORITIES
1. **SIMPLICITY FIRST** - Simple designs, simple code, simple solutions
2. **TIME COMPLEXITY** - Always optimize for O(n), avoid O(n²) at all costs  
3. **SPACE COMPLEXITY** - Minimal memory usage, efficient data structures

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

### 🎯 SIMPLICITY IS MANDATORY
- **ALWAYS write simple, readable code** - complexity is forbidden
- **Use standard design patterns** - NEVER invent custom complex solutions
- **Prefer clear, explicit code** over clever, compact code
- **Write code that any developer can understand** in 5 minutes
- **Simple design beats complex optimization** - readable code is maintainable code
- **One responsibility per method/class** - do one thing well

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

### ⚡ PERFORMANCE IS CRITICAL
- **ALWAYS analyze time complexity FIRST** - before writing any algorithm
- **O(n²) is FORBIDDEN** - always find O(n) or O(log n) solutions
- **Use correct data structures** - HashMap for lookups, ArrayList for iteration
- **Space complexity matters** - minimize memory allocation and usage
- **Profile before optimizing** - but design for performance from start

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

### 🏗️ SPACE-EFFICIENT Data Structure Choices:
- **ArrayList**: For iteration, indexed access - O(1) get, O(n) search, LOW memory overhead
- **HashMap**: For key-based lookups - O(1) average get/put, reasonable memory usage
- **HashSet**: For membership testing - O(1) contains, efficient for unique checks
- **LinkedList**: AVOID - high memory overhead, poor cache locality
- **Primitive arrays**: Use when size is known - minimal memory footprint
- **StringBuilder**: For string concatenation - avoid String + operations

## 🎯 MANDATORY Code Quality Rules (In Priority Order)
1. **SIMPLICITY FIRST**: Simple designs, simple code - complexity is the enemy
2. **TIME COMPLEXITY**: O(n²) is forbidden, always optimize for O(n) or better
3. **SPACE COMPLEXITY**: Minimize memory usage, choose efficient data structures
4. **Dependency Injection**: Use constructor injection for all dependencies
5. **Constants**: Create constants for repeated string values  
6. **Standard Patterns**: Use well-known design patterns, avoid custom complex solutions
7. **Readability**: Write code that any developer can understand in 5 minutes
8. **Error Handling**: Ensure proper error handling and validation
9. **Localization**: Any prompt or error message originating from a sheet should be localizable

## ⚠️ Performance vs Simplicity Balance
- **Design for performance from start** - don't wait to optimize
- **Simple O(n) beats complex O(log n)** - readability matters
- **Profile critical paths** - measure before micro-optimizing
- **NEVER sacrifice O(n) for readability** - time complexity comes first
- **Space-time tradeoffs** - consider both memory and speed
- **Simplicity wins** - unless performance is critically impacted
