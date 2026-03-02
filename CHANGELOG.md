# Changelog

All notable changes to this project are documented in this file.

## 2026-03-02

### Reporting hierarchy expansion
- Extended Trial Balance, Balance Sheet, and Income Statement line outputs with hierarchy metadata (`parentAccountCode`, `hierarchyLevel`, `leaf`) and rolled-up analysis fields.
- Updated statement tree rendering to include full allowed-type CoA hierarchy while preserving own-value totals for accounting invariants.
- Synced static OpenAPI schemas with the new reporting line fields.

### Documentation synchronization and version alignment
- Updated documentation status/version metadata to reflect current implementation state.
- Corrected stale phase/gap completion markers in `docs/10-audit-remediation-plan.md`.
- Added current closure-status addendum in `docs/finance-accounting-gap-analysis.md`.
- Clarified hierarchy-aware reporting contract notes in `docs/05-api-contracts.md`.

## 2026-02-26

### Audit remediation completion (R1-R8)
- R1: Serialized hash-chain progression per tenant to prevent chain forks under concurrency.
- R2: Added PostgreSQL-backed idempotency fallback path when Redis is unavailable.
- R3: Added production fail-fast guard to prevent security bypass configuration.
- R4: Migrated JWT verification to asymmetric signing path (RS256) with rotation-ready ops guidance.
- R5: Replaced Lombok `@Data` on JPA entities with safer entity semantics.
- R6: Expanded critical service unit-test coverage and stabilized regression behavior.
- R7: Added bounded LRU cache for SpEL expression compilation in mapping-rule evaluation.
- R8: Aligned migration/version references across docs and added explicit migration numbering notes.

### Documentation alignment
- Updated migration references in:
  - `docs/SRS.md`
  - `docs/08-implementation-roadmap.md`
  - `docs/04-database-schema.md`
  - `docs/10-audit-remediation-plan.md`
