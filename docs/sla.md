# FIS-Engine Service Level Agreement (SLA)

## Document Control

| Field | Value |
|-------|-------|
| **Version** | 1.0 |
| **Effective Date** | April 13, 2026 |
| **Owner** | FIS Platform SRE Team |
| **Review Cycle** | Quarterly |
| **Classification** | Internal |

---

## 1. Service Overview

FIS-Engine is a multi-tenant financial information processing system providing:
- Double-entry journal entry posting with hash-chain tamper detection
- Multi-currency account management
- Automated event-driven accounting via outbox pattern
- Period-end revaluation and currency translation
- Audit logging and regulatory compliance

---

## 2. Availability

### 2.1 Target

| Metric | Target | Calculation |
|--------|--------|-------------|
| **Monthly Uptime** | 99.99% | `(Total Minutes - Downtime) / Total Minutes × 100` |
| **Maximum Downtime** | 4.32 minutes/month | 43,200 seconds/month × 0.01% |
| **Annual Uptime** | 99.99% | 52.56 minutes/year maximum downtime |

### 2.2 Downtime Definition

Downtime is defined as any consecutive 60-second period during which:
- The `/actuator/health` endpoint returns a non-200 status code, OR
- The HTTP error rate exceeds 5% for 60+ seconds, OR
- The database is unreachable and no failover is in progress

### 2.3 Exclusions

The following do **not** count as downtime:
- Scheduled maintenance windows (announced 72+ hours in advance)
- Force majeure events (natural disasters, acts of war)
- Customer-caused issues (misconfigured API keys, incorrect payloads)
- Third-party service failures outside FIS-Engine's control
- Planned deployments with zero-downtime rolling updates

---

## 3. Performance

### 3.1 Latency Targets

Measured at the application layer (excluding network transit):

| Percentile | Target | Measurement |
|------------|--------|-------------|
| **p50** | < 25ms | 50th percentile response time |
| **p95** | < 100ms | 95th percentile response time |
| **p99** | < 200ms | 99th percentile response time |

### 3.2 Throughput

| Metric | Target | Notes |
|--------|--------|-------|
| **Journal Entries** | > 1,000 entries/second | Sustained throughput under normal load |
| **Account Creation** | > 500 accounts/second | Bulk import scenario |
| **Concurrent Users** | > 10,000 API connections | Simultaneous authenticated sessions |
| **Peak Burst** | 5,000 entries/second for 30s | Spike tolerance (auto-scaling triggers) |

### 3.3 Resource Utilization

| Resource | Warning | Critical |
|----------|---------|----------|
| CPU | > 70% average | > 90% for 5+ minutes |
| Memory | > 80% of limit | > 95% of limit (OOM risk) |
| Database Connections | > 80% of pool | > 95% of pool |
| Disk I/O | > 70% IOPS | > 90% IOPS |

---

## 4. Data Durability

### 4.1 Recovery Objectives

| Metric | Target | Description |
|--------|--------|-------------|
| **RPO** (Recovery Point Objective) | < 5 minutes | Maximum data loss in a disaster scenario |
| **RTO** (Recovery Time Objective) | < 1 hour | Time to restore full service after disaster |

### 4.2 Backup Strategy

| Component | Frequency | Retention | Storage |
|-----------|-----------|-----------|---------|
| PostgreSQL (full) | Daily at 02:00 UTC | 30 days | S3 (Standard-IA, AES256) |
| PostgreSQL (WAL) | Continuous (streaming) | 7 days | EBS volumes (GP3, encrypted) |
| Redis | In-memory replication | N/A | 2 replicas across AZs |
| RabbitMQ | Quorum queues (persistent) | N/A | 3-node cluster, persistent PVs |
| Configuration | Git version control | Unlimited | GitHub repository |

### 4.3 Data Integrity

- All journal entries include a SHA-256 hash chain for tamper detection
- Hash chains are verified daily via automated integrity checks
- Any hash chain break triggers an immediate P1 incident

---

## 5. Error Budget

### 5.1 Monthly Error Budget

| Metric | Budget | Calculation |
|--------|--------|-------------|
| **Error Rate** | 0.01% of requests | 1 error per 10,000 requests |
| **Monthly Budget** | 10,000 errors (at 100M requests/month) | `Total Requests × 0.0001` |

### 5.2 Error Budget Policy

| Budget Remaining | Action |
|-----------------|--------|
| **> 50%** | Normal operations |
| **25-50%** | Increased monitoring, review recent changes |
| **10-25%** | **Feature freeze**: no new deployments without SRE approval |
| **< 10%** | **Deployment freeze**: only critical bug fixes and security patches |
| **Exhausted** | **Full freeze**: all deployments halted until budget recovers |

### 5.3 Budget Recovery

- Error budget resets at the start of each calendar month
- Budget does **not** roll over between months
- If exhausted, budget recovers naturally as the month progresses (no carry-forward penalty)

---

## 6. Incident Response

### 6.1 Severity Levels

| Severity | Definition | Response Time | Resolution Target |
|----------|-----------|---------------|-------------------|
| **P1 - Critical** | Complete service outage, data loss | 5 minutes | 30 minutes |
| **P2 - High** | Significant degradation, > 50% of requests affected | 15 minutes | 2 hours |
| **P3 - Medium** | Partial degradation, < 50% of requests affected | 1 hour | 8 hours |
| **P4 - Low** | Minor issue, workaround available | 4 hours | 48 hours |

### 6.2 Communication

| Event | Channel | Timeline |
|-------|---------|----------|
| P1 detected | PagerDuty → Slack #fis-incidents | Immediate |
| P1 update | Slack + status page | Every 15 minutes |
| P2 update | Slack | Every 30 minutes |
| Incident resolved | Slack + email | Within 1 hour |
| Post-mortem | Document in Confluence | Within 5 business days |

---

## 7. Security

### 7.1 Compliance

| Standard | Status | Notes |
|----------|--------|-------|
| SOC 2 Type II | Target | Annual audit |
| GDPR | Compliant | Data residency in EU regions |
| PCI DSS | Target | For payment-related journals |

### 7.2 Security Controls

- **Encryption at rest**: AES-256 for all persistent volumes and S3 backups
- **Encryption in transit**: TLS 1.3 for all internal and external communication
- **Access control**: OAuth2/OIDC with JWT validation, tenant isolation
- **Secrets management**: HashiCorp Vault or AWS Secrets Manager (no secrets in Git)
- **Network segmentation**: Kubernetes NetworkPolicies with default-deny
- **Vulnerability scanning**: Trivy scans in CI/CD pipeline; Snyk for dependencies

### 7.3 Vulnerability Response

| Severity | Patch Timeline |
|----------|----------------|
| **Critical (CVSS 9.0+)** | 24 hours |
| **High (CVSS 7.0-8.9)** | 7 days |
| **Medium (CVSS 4.0-6.9)** | 30 days |
| **Low (CVSS < 4.0)** | 90 days |

---

## 8. Monitoring & Reporting

### 8.1 Dashboards

| Dashboard | Audience | URL |
|-----------|----------|-----|
| Real-time SLO | SRE Team | Grafana: `/d/fis-slo` |
| Business Metrics | Product Team | Grafana: `/d/fis-business` |
| Infrastructure | Platform Team | Grafana: `/d/fis-infra` |
| Public Status | All Users | `status.fis-engine.com` |

### 8.2 Monthly SLA Report

| Section | Content |
|---------|---------|
| Uptime percentage | Actual vs. target |
| Latency percentiles | p50, p95, p99 actuals |
| Error rate | Total errors, error budget remaining |
| Incidents | Count by severity, MTTR |
| Deployments | Count, success rate, rollback rate |
| Capacity | Resource utilization trends |

---

## 9. Change Management

### 9.1 Deployment Policy

| Environment | Frequency | Approval |
|-------------|-----------|----------|
| **Staging** | On merge to main | Automated (CI/CD) |
| **Production** | Trunk-based (daily) | Manual gate (SRE approval) |

### 9.2 Rollback Policy

- Any production deployment can be rolled back within **5 minutes**
- Rollback is automatic if error rate exceeds 1% within 5 minutes post-deployment
- Rollback does **not** count against error budget

---

## 10. Review & Amendments

This SLA is reviewed quarterly. Amendments require:
1. Proposal by SRE team or stakeholder
2. Impact analysis (availability, cost, engineering effort)
3. Approval by Platform Engineering Lead
4. 30-day notice to all stakeholders before changes take effect

---

## Appendix A: Glossary

| Term | Definition |
|------|------------|
| **RPO** | Recovery Point Objective — maximum acceptable data loss |
| **RTO** | Recovery Time Objective — maximum time to restore service |
| **SLO** | Service Level Objective — internal target (stricter than SLA) |
| **SLI** | Service Level Indicator — measured metric |
| **MTTR** | Mean Time to Recovery — average incident resolution time |
| **PDB** | Pod Disruption Budget — Kubernetes HA mechanism |
| **HPA** | Horizontal Pod Autoscaler — Kubernetes auto-scaling |

## Appendix B: Related Documents

- [Capacity Planning Guide](./capacity-planning.md)
- [Disaster Recovery Runbook](../deploy/k8s/disaster-recovery/failover-README.md)
- [Architecture Decision Records](./adr/)
- [Incident Response Playbook](./incident-response.md)
