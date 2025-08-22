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

## Code Quality Rules
1. Use constructor injection for all dependencies
2. Create constants for repeated string values
3. Follow immutability principles where possible
4. Ensure proper error handling and validation