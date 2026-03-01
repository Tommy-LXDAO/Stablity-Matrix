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

### Phase 1: Task Selection

1. Read `task.json` to understand all available tasks
2. Select the **lowest ID** task where `passes: false`
3. Check task category and dependencies:
   - `config` tasks (id 1-2): Must complete first
   - `testing` tasks (id 3-10): Require config tasks to pass
   - `refactor` tasks (id 11-15): Require testing infrastructure
   - `feature`/`integration` tasks (id 16-17): Require prior tasks
4. If dependencies not met, report blocking issue

### Phase 2: Analysis & Planning

1. Read task description and all steps carefully
2. Identify files that need to be created or modified
3. Review existing code patterns in the codebase
4. Create a mental plan before writing any code

### Phase 3: Implementation

1. **For testing tasks**: Follow TDD (Red-Green-Refactor)
   - Write failing test first
   - Implement minimal code to pass
   - Refactor if needed
2. **For refactor tasks**:
   - Create new interface file
   - Create implementation class
   - Update dependency injection
   - Ensure existing tests still pass
3. **For config tasks**:
   - Verify environment setup
   - Test connectivity (Redis, APIs)

### Phase 4: Verification

1. Run `mvn test` - ALL tests must pass
2. If tests fail:
   - Analyze failure messages
   - Fix issues
   - Re-run tests
3. For integration: run `mvn verify` if applicable
4. Verify no compilation errors: `mvn compile`

### Phase 5: Documentation

1. Update `progress.txt`:
   ```
   ## YYYY-MM-DD - Task #X: [Task Name]

   ### Completed
   - Step 1: description
   - Step 2: description

   ### Files Created
   - path/to/file1.java

   ### Files Modified
   - path/to/file2.java

   ### Test Results
   - All tests passing: [X/total]
   ```

2. Update `task.json`: Set `"passes": true` for completed task

### Phase 6: Commit

1. Stage all changes: `git add <specific files>`
2. Create commit with descriptive message:
   ```
   Task #X: [Description]

   - Completed step 1
   - Completed step 2

   Co-Authored-By: Claude <noreply@anthropic.com>
   ```
3. Do NOT push unless explicitly requested

## Key Rules

- **One task per session** - Complete one task fully before stopping
- **Tests must pass** - Never mark complete if tests fail
- **Document everything** - progress.txt must reflect all changes
- **Atomic commits** - Include code, progress.txt, and task.json together
- **Preserve tasks** - Never delete tasks, only flip `passes` flag
- **Report blockers** - Stop and document if:
  - External dependencies unavailable (Redis, API keys)
  - Cannot resolve test failures after 3 attempts
  - Missing configuration that cannot be created
  - Circular dependencies detected

## Error Recovery

If encountering persistent errors:

1. **Build errors**: Check pom.xml dependencies, Java version
2. **Test failures**: Read stack trace, check assertions
3. **Config errors**: Verify environment variables, Redis connection
4. **If stuck after 3 attempts**:
   - Add `blocked: true` and `blockedReason: "..."` to task in task.json
   - Document in progress.txt
   - Move to next task in next session

## Task Categories Reference

| Category | IDs | Description | Prerequisites |
|----------|-----|-------------|---------------|
| config | 1-2 | Environment setup | None |
| testing | 3-10 | TDD test creation | Config complete |
| refactor | 11-15 | Interface extraction | Tests exist |
| feature | 16 | New functionality | Refactor done |
| integration | 17 | E2E tests | All above |
