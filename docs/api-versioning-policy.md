# API Versioning Policy

## Overview

The FIS Engine uses **URL-based versioning** (`/v1/`, `/v2/`, ...) combined with an
optional `API-Version` request header for explicit version negotiation.

All versioned endpoints are annotated with `@ApiVersion(N)` which drives runtime
version resolution, deprecation signaling, and sunset enforcement.

---

## Version Lifecycle

Every API major version follows a three-stage lifecycle:

| Stage | Duration | Behavior |
|-------|----------|----------|
| **Active** | Indefinite | Normal operation. No extra headers. |
| **Deprecated** | Minimum 12 months from deprecation date | Responses carry `Deprecation: true`, `Sunset: <date>`, `Link: <successor>` headers. |
| **Retired** | After sunset date passes | Returns **HTTP 410 Gone** with a migration hint in the response body. |

### Timeline Example

```
v1 Released:     2025-01-01  (Active)
v2 Released:     2026-06-01  (Active)
v1 Deprecated:   2026-06-01  (Deprecated — sunset set to 2027-06-01)
v1 Retired:      2027-06-01  (410 Gone)
```

**Minimum deprecation window: 12 months.** Consumers must have at least one year to
migrate after a version is marked deprecated.

---

## Version Resolution Order

1. **`API-Version` request header** (highest priority)
   - `API-Version: 2` → resolves to v2 regardless of URL path
2. **URL path segment** `/v{N}/`
   - `GET /v1/accounts` → resolves to v1
3. **`@ApiVersion` annotation** on the controller/method
   - Fallback when neither header nor URL pattern is present

Unsupported versions receive **HTTP 406 Not Acceptable**.

---

## Breaking vs. Non-Breaking Changes

### Breaking Changes (require a new major version)

A change is **breaking** if an existing client integrating against version N would
experience errors, incorrect behavior, or data loss without modifying their code.

| Category | Examples |
|----------|----------|
| **Contract** | Removing a response field, changing a field type, renaming an endpoint |
| **Behavioral** | Changing default sort order, altering validation rules, changing error codes |
| **Authentication** | Adding required scopes, changing auth mechanism |
| **Rate Limits** | Lowering rate limits beyond documented SLA |

### Non-Breaking Changes (same major version, backward-compatible)

| Category | Examples |
|----------|----------|
| **Additive** | Adding new endpoints, adding optional request parameters, adding new response fields |
| **Relaxation** | Removing a validation constraint, widening accepted input types |
| **Performance** | Improving response times, adding caching |
| **Deprecation** | Marking an endpoint deprecated (with proper sunset date) |

---

## Response Headers

### Active Endpoint

```
HTTP/2 200 OK
API-Version: 1
```

### Deprecated Endpoint

```
HTTP/2 200 OK
API-Version: 1
Deprecation: true
Sunset: Sun, 31 Dec 2027 00:00:00 GMT
Link: </v2/accounts/export>; rel="successor-version"
```

### Retired Endpoint

```
HTTP/2 410 Gone
Content-Type: application/problem+json

{
  "status": 410,
  "error": "Gone",
  "message": "This API version has been retired on 2027-12-31. Migrate to /v2/accounts/export"
}
```

---

## Migration Guide Template

When a new major version is released, consumers should follow this migration checklist:

### Migration: v{N} → v{N+1}

#### 1. Review Changelog

See `CHANGELOG.md` for a complete list of changes in v{N+1}.

#### 2. Identify Breaking Changes

| v{N} Endpoint | v{N+1} Endpoint | Change Description | Effort |
|---------------|-----------------|--------------------|--------|
| `GET /vN/...` | `GET /v{N+1}/...` | Describe what changed | Low/Med/High |

#### 3. Update Request Headers

```diff
- API-Version: {N}
+ API-Version: {N+1}
```

#### 4. Update Base URL

```diff
- https://api.example.com/vN/...
+ https://api.example.com/v{N+1}/...
```

#### 5. Test Against Staging

Deploy changes against the staging environment first. Run the full integration test suite.

#### 6. Monitor Deprecation Headers

Check response headers for `Deprecation` and `Sunset` to track remaining endpoints
that need migration.

#### 7. Sunset Deadline

**v{N} sunset date:** YYYY-MM-DD
After this date, all v{N} requests will return HTTP 410 Gone.

---

## Configuration

API versioning is controlled via application properties:

```yaml
fis:
  api:
    version:
      supported: [1, 2]   # Set of supported major versions
      current: 2          # Latest active major version
```

### Adding a New Version

1. Create new controllers under `/v{N}/` paths or annotate with `@ApiVersion({N})`.
2. Add the new version to `fis.api.version.supported`.
3. Bump `fis.api.version.current` to the new version.
4. Mark old endpoints as deprecated: `@ApiVersion(value = 1, deprecated = true, sunset = "...")`.
5. Update security rules in `SecurityConfig` if new routes have different access patterns.
6. Update this document and the `CHANGELOG.md`.

---

## Implementation Details

| Component | Location |
|-----------|----------|
| Annotation | `com.bracit.fisprocess.annotation.ApiVersion` |
| Interceptor | `com.bracit.fisprocess.config.ApiVersionInterceptor` |
| Config | `com.bracit.fisprocess.config.ApiVersioningConfig` |
| Security | `com.bracit.fisprocess.config.SecurityConfig` (regex-based version-agnostic RBAC) |
| Tests | `com.bracit.fisprocess.config.ApiVersionInterceptorTest` |
