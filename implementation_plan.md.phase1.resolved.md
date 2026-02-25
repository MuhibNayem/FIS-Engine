# Phase 1: Foundation & Infrastructure Setup

Build the project skeleton, database connectivity, schema migrations, foundational domain entities, Account CRUD APIs, and a global exception handler — all with full test coverage.

## Spring Boot 4.0.3 Key Facts (Verified)

| Aspect | Detail |
|:-------|:-------|
| **Framework** | Spring Framework 7, Jakarta EE 11, Servlet 6.1 |
| **ORM** | Hibernate 7.1, Jakarta Persistence 3.2 |
| **JSON** | Jackson 3 (`tools.jackson.*` packages, immutable `JsonMapper` replaces `ObjectMapper`) |
| **DB Migration** | Flyway 11 — requires `spring-boot-starter-flyway` (not just `flyway-core`) |
| **Connection Pool** | HikariCP 7 |
| **Virtual Threads** | Default for Tomcat/Jetty — no explicit config needed |
| **Null Safety** | JSpecify `@NullMarked` integrated platform-wide |
| **Modular Starters** | Every technology has its own `spring-boot-<tech>` module + matching `*-test` starter |
| **Existing starters** | `spring-boot-h2console`, `*-data-jdbc-test`, `*-data-jpa-test`, `*-webmvc-test` are **valid** Spring Boot 4 modular names |

## User Review Required

> [!IMPORTANT]
> **[application.properties](file:///home/amnayem/Projects/fis-process/src/main/resources/application.properties) → `application.yml`:** The SRS specifies YAML-based configuration with environment-variable-driven profiles. I will replace the [.properties](file:///home/amnayem/Projects/fis-process/src/main/resources/application.properties) file with a structured `application.yml`.

---

## Proposed Changes

### Build & Configuration

#### [MODIFY] [build.gradle](file:///home/amnayem/Projects/fis-process/build.gradle)
- **Keep** existing valid starters: `spring-boot-h2console`, `spring-boot-starter-data-jdbc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-redis`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc` and all matching `*-test` starters.
- **Add**: `spring-boot-starter-flyway`, `flyway-database-postgresql`, `spring-boot-starter-actuator`, `modelmapper`, `jspecify`.
- **Add test**: `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`.

#### [DELETE] [application.properties](file:///home/amnayem/Projects/fis-process/src/main/resources/application.properties)
- Replaced by `application.yml`.

#### [NEW] [application.yml](file:///home/amnayem/Projects/fis-process/src/main/resources/application.yml)
- Spring Data JPA config with `hibernate.ddl-auto=validate`.
- Flyway enabled with `locations: classpath:db/migration`.
- PostgreSQL datasource with `${ENV_VAR}` placeholders.
- H2 console enabled for dev profile.
- Virtual Threads enabled (`spring.threads.virtual.enabled=true`).

---

### Flyway Migrations (V1–V2)

#### [NEW] [V1__create_business_entity.sql](file:///home/amnayem/Projects/fis-process/src/main/resources/db/migration/V1__create_business_entity.sql)
- `fis_business_entity` table as defined in [docs/04-database-schema.md](file:///home/amnayem/Projects/fis-process/docs/04-database-schema.md).

#### [NEW] [V2__create_accounts.sql](file:///home/amnayem/Projects/fis-process/src/main/resources/db/migration/V2__create_accounts.sql)
- `fis_account` table with parent-child hierarchy, indexes as defined in [docs/04-database-schema.md](file:///home/amnayem/Projects/fis-process/docs/04-database-schema.md).

---

### Domain Layer (Entities & Enums)

Base package: `com.bracit.fisprocess`

#### [NEW] [package-info.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/package-info.java)
- `@NullMarked` annotation on root package.

#### [NEW] [AccountType.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/domain/enums/AccountType.java)
- Enum: `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE`.

#### [NEW] [BusinessEntity.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/domain/entity/BusinessEntity.java)
- JPA entity mapped to `fis_business_entity` as specified in [docs/07-domain-models.md](file:///home/amnayem/Projects/fis-process/docs/07-domain-models.md).
- Uses Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- `@PrePersist` / `@PreUpdate` lifecycle callbacks.

#### [NEW] [Account.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/domain/entity/Account.java)
- JPA entity mapped to `fis_account` with `AccountType` enum, `parentAccount` self-referencing `@ManyToOne`, and `currentBalance` as `Long` (cents).

#### [NEW] [package-info.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/domain/package-info.java)
- `@NullMarked` on domain package.

---

### Repository Layer

#### [NEW] [BusinessEntityRepository.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/repository/BusinessEntityRepository.java)
- Spring Data JPA `JpaRepository<BusinessEntity, UUID>`.
- Custom query: `findByTenantIdAndIsActiveTrue(UUID tenantId)`.

#### [NEW] [AccountRepository.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/repository/AccountRepository.java)
- Spring Data JPA `JpaRepository<Account, UUID>`.
- Custom queries: `findByTenantIdAndCode()`, `findByTenantId()` with `Pageable`, `existsByTenantIdAndCode()`.

#### [NEW] [package-info.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/repository/package-info.java)
- `@NullMarked`.

---

### DTO Layer

#### [NEW] [CreateAccountRequestDto.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/dto/request/CreateAccountRequestDto.java)
- Fields: `code`, `name`, `accountType`, `currencyCode`, `parentAccountCode` (nullable). Jakarta `@NotBlank`/`@NotNull` validation.

#### [NEW] [UpdateAccountRequestDto.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/dto/request/UpdateAccountRequestDto.java)
- Fields: `name` (nullable), `isActive` (nullable).

#### [NEW] [AccountResponseDto.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/dto/response/AccountResponseDto.java)
- Fields matching [docs/07-domain-models.md](file:///home/amnayem/Projects/fis-process/docs/07-domain-models.md) Section 5.2.

---

### Service Layer

#### [NEW] [AccountService.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/service/AccountService.java)
- Interface with SRP-focused methods: `createAccount()`, `getAccountByCode()`, `listAccounts()`, `updateAccount()`.

#### [NEW] [AccountServiceImpl.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/service/impl/AccountServiceImpl.java)
- Implements `AccountService`. Uses `AccountRepository`, `BusinessEntityRepository`, and ModelMapper.
- Validates tenant exists, account code uniqueness, parent account existence.
- Prevents deletion of accounts (only deactivation via `isActive = false`).

#### [NEW] [package-info.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/service/package-info.java)
- `@NullMarked`.

---

### Controller Layer

#### [NEW] [AccountController.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/controller/AccountController.java)
- `@RestController` at `/v1/accounts`.
- Extracts `X-Tenant-Id` from request header.
- Endpoints: `POST`, `GET` (list), `GET /{code}`, `PATCH /{code}`.
- Uses `@Valid` for request body validation.

#### [NEW] [package-info.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/controller/package-info.java)
- `@NullMarked`.

---

### Configuration Layer

#### [NEW] [ModelMapperConfig.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/config/ModelMapperConfig.java)
- `@Configuration` bean providing a configured `ModelMapper` instance.

---

### Global Exception Handling

#### [NEW] [GlobalExceptionHandler.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/exception/GlobalExceptionHandler.java)
- `@ControllerAdvice` extending `ResponseEntityExceptionHandler`.
- Handles: `AccountNotFoundException` → 404, `ValidationFailedException` → 400, `FisBusinessException` → 422, `MethodArgumentNotValidException` → 400.
- All responses return RFC 7807 `ProblemDetail`.

#### [NEW] [FisBusinessException.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/exception/FisBusinessException.java)
- Abstract base for all domain exceptions.

#### [NEW] [AccountNotFoundException.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/exception/AccountNotFoundException.java)
- Extends `FisBusinessException`. Used when account code not found.

#### [NEW] [DuplicateAccountCodeException.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/exception/DuplicateAccountCodeException.java)
- Extends `FisBusinessException`. Used when account code already exists for tenant.

#### [NEW] [TenantNotFoundException.java](file:///home/amnayem/Projects/fis-process/src/main/java/com/bracit/fisprocess/exception/TenantNotFoundException.java)
- Extends `FisBusinessException`. Used when `X-Tenant-Id` references a non-existent or inactive entity.

---

## Verification Plan

### Automated Tests

All tests use JUnit 5 + Spring Boot Test. Integration tests use **Testcontainers PostgreSQL** to verify Flyway migrations and real DB interactions.

#### Unit Tests

##### [NEW] [AccountServiceImplTest.java](file:///home/amnayem/Projects/fis-process/src/test/java/com/bracit/fisprocess/service/impl/AccountServiceImplTest.java)
- Test `createAccount()` happy path → returns `AccountResponseDto` with balance = 0.
- Test `createAccount()` with duplicate code → throws `DuplicateAccountCodeException`.
- Test `createAccount()` with invalid tenant → throws `TenantNotFoundException`.
- Test `createAccount()` with parent account that doesn't exist → throws `AccountNotFoundException`.
- Test `getAccountByCode()` happy path → returns correct DTO.
- Test `getAccountByCode()` not found → throws `AccountNotFoundException`.
- Test `updateAccount()` deactivation → `isActive = false`.
- Test `updateAccount()` name change → name is updated.
- Test `listAccounts()` returns paginated results.

#### Integration Tests

##### [NEW] [FlywayMigrationTest.java](file:///home/amnayem/Projects/fis-process/src/test/java/com/bracit/fisprocess/migration/FlywayMigrationTest.java)
- Verifies V1 and V2 migrations execute cleanly on Testcontainers PostgreSQL.

##### [NEW] [AccountControllerIntegrationTest.java](file:///home/amnayem/Projects/fis-process/src/test/java/com/bracit/fisprocess/controller/AccountControllerIntegrationTest.java)
- `POST /v1/accounts` → 201 Created with correct response body.
- `POST /v1/accounts` with duplicate code → returns RFC 7807 error.
- `POST /v1/accounts` with missing required fields → 400 Validation Failed.
- `GET /v1/accounts/{code}` → 200 OK with correct account.
- `GET /v1/accounts/{code}` not found → 404.
- `PATCH /v1/accounts/{code}` deactivation → 200 OK, `isActive = false`.
- `GET /v1/accounts` → 200 OK paginated list.
- Missing `X-Tenant-Id` header → 400.

**Command to run all tests:**
```bash
./gradlew test
```

### Manual Verification
- After all automated tests pass, run `./gradlew bootRun` and hit `GET /actuator/health` → should return `{"status": "UP"}`.
