# Changelog

All notable changes to this project are documented in this file.

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
