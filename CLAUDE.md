# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Stability Matrix** is an Android crash analysis platform that parses tombstone files (ANR/crash logs) and uses AI for fault analysis. It's built with Java Spring Boot.

## Commands

```bash
# Build the project
mvn clean install

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run specific test method
mvn test -Dtest=ClassName#methodName

# Skip tests during build
mvn clean install -DskipTests

# Run the application
mvn spring-boot:run

# Package without running tests
mvn package -DskipTests
```

## Architecture

### Layered Architecture

```
Controller Layer (REST APIs)
        │
        ▼
Service Layer (Business Logic - Interfaces)
        │
        ▼
Implementation Layer (Concrete Implementations)
        │
        ▼
Data/Config Layer (Redis, File System, AI APIs)
```

### Key Components

- **Controllers**: `AnalysisController`, `SessionController`, `TestController`
- **Services**: File parsing, AI analysis, session management, pattern matching
- **Pattern Matchers**: SIGSEGV, SIGABRT, SIGBUS, SIGFPE, SIGILL, SIGPIPE
- **File Parsing**: Android AArch64 tombstone file parser

### Data Models

- `TroubleEntity` - General fault entity
- `AArch64Tombstone` - AArch64 tombstone file parse result
- Signal/Stack/Register dump information

## Configuration

Key settings in `application.yaml`:
- Server port: `8888`
- Redis: `127.0.0.1:6379`
- File storage: `/tmp/sessions`
- AI API: Configure via `NEWAPI_KEY`, `NEWAPI_BASE_URL` environment variables

## TDD Workflow (Test-Driven Development)

Follow the Red-Green-Refactor cycle:

1. **Red** - Write a failing test first
   ```bash
   # Create test class in src/test/java/
   mvn test -Dtest=NewServiceTest  # Should fail
   ```

2. **Green** - Write minimal code to make test pass

3. **Refactor** - Clean up code while keeping tests passing

### Testing Best Practices

- Place tests in `Demo/src/test/java/com/stability/martrix/`
- Use JUnit 5 and Mockito for mocking
- Test file naming: `ClassNameTest.java` or `ClassNameIT.java` for integration tests
- Use `@SpringBootTest` for integration tests, `@ExtendWith(MockitoExtension.class)` for unit tests

### Common Test Patterns

```java
// Unit test with Mockito
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock
    private Dependency dependency;

    @InjectMocks
    private MyService service;

    @Test
    void shouldDoSomething() {
        // Arrange
        when(dependency.someMethod()).thenReturn(value);

        // Act
        Result result = service.doSomething();

        // Assert
        assertThat(result).isNotNull();
    }
}

// Integration test with Spring
@SpringBootTest
class MyControllerIT {
    @Autowired
    private WebTestClient client;

    @Test
    void shouldReturnOk() {
        client.get().uri("/endpoint")
            .exchange()
            .expectStatus().isOk();
    }
}
```

### Test Resources

- Test data files: `Demo/src/test/resources/`
- Use `@TestPropertySource` or `application-test.yaml` for test configuration

### Running Tests

```bash
# Run all tests
mvn test

# Run unit tests only (exclude integration tests)
mvn test -Dtest='!*IT,!*IntegrationTest'

# Run integration tests only
mvn test -Dtest='*IT,*IntegrationTest'

# Run with coverage report
mvn test jacoco:report
```

## Agent Workflow

1. Read `task.json` and select ONE task where `passes: false`
2. Implement the task following existing code patterns
3. Test: run `mvn test` to verify
4. Update `progress.txt` with completed work
5. Update `task.json` to set `passes: true`
6. Commit all changes together

## Key Rules

- One task per session
- Test must pass before marking complete
- Document progress in progress.txt
- Commit code, progress.txt, and task.json together
- Never remove tasks, only flip passes flag
- Stop and report if blocked (missing config, external dependencies unavailable)
