# FIS-Engine Disaster Recovery Runbook

## Table of Contents
1. [Recovery Time Objective (RTO)](#recovery-time-objective-rto)
2. [Recovery Point Objective (RPO)](#recovery-point-objective-rpo)
3. [Backup Procedures](#backup-procedures)
4. [Point-In-Time Recovery](#point-in-time-recovery)
5. [Failover Procedures](#failover-procedures)
6. [Post-Recovery Verification](#post-recovery-verification)

---

## Recovery Time Objective (RTO)

| Tier | Target | Description |
|------|--------|-------------|
| Critical | 15 minutes | Complete service restoration |
| High | 1 hour | Core journal posting restored |
| Medium | 4 hours | Full functionality restored |

## Recovery Point Objective (RPO)

| Data Type | Target | Description |
|----------|--------|-------------|
| Journal Entries | 5 minutes | WAL-based PITR |
| Configuration | 24 hours | Daily snapshots |
| Audit Logs | 5 minutes | Real-time replication |

---

## Backup Procedures

### Automated Daily Backup (PostgreSQL)

```bash
#!/bin/bash
# Location: /opt/fis-engine/scripts/backup.sh
# Run via cron: 0 2 * * * /opt/fis-engine/scripts/backup.sh

BACKUP_DIR="/backups/postgres"
DATE=$(date +%Y%m%d_%H%M%S)
RETENTION_DAYS=30

# Create backup directory
mkdir -p ${BACKUP_DIR}

# Full database backup with WAL
pg_basebackup -h localhost -U fis_user -D ${BACKUP_DIR}/base_${DATE} -Ft -z -P

# Compress and encrypt
tar -czf ${BACKUP_DIR}/backup_${DATE}.tar.gz -C ${BACKUP_DIR} base_${DATE}
rm -rf ${BACKUP_DIR}/base_${DATE}

# Upload to S3/GCS
aws s3 cp ${BACKUP_DIR}/backup_${DATE}.tar.gz s3://fis-engine-backups/postgres/

# Cleanup old backups
find ${BACKUP_DIR} -name "*.tar.gz" -mtime +${RETENTION_DAYS} -delete
```

### Continuous WAL Archiving

```sql
-- PostgreSQL configuration (postgresql.conf)
wal_level = replica
max_wal_senders = 3
wal_keep_size = 1GB
archive_mode = on
archive_command = 'aws s3 cp %p s3://fis-engine-backups/wal/%f'
```

### Redis Backup

```bash
# Redis RDB backup (run every 5 minutes via cron)
redis-cli BGSAVE
aws s3 cp /var/lib/redis/dump.rdb s3://fis-engine-backups/redis/
```

### Application Configuration Backup

```bash
# Backup application config and secrets
tar -czf config_backup_$(date +%Y%m%d).tar.gz \
    /opt/fis-engine/config/application-prod.yml \
    /opt/fis-engine/secrets/

aws s3 cp config_backup_*.tar.gz s3://fis-engine-backups/config/
```

---

## Point-In-Time Recovery

### Step 1: Stop Application

```bash
# Stop all FIS-Engine instances
kubectl scale deployment fis-engine --replicas=0

# Or for systemd:
systemctl stop fis-engine@
```

### Step 2: Restore Database

```bash
# Download latest base backup
aws s3 cp s3://fis-engine-backups/postgres/backup_20240420_020000.tar.gz /tmp/

# Extract backup
tar -xzf /tmp/backup_*.tar.gz -C /tmp/

# Stop PostgreSQL
systemctl stop postgresql

# Clear data directory
rm -rf /var/lib/postgresql/data/*

# Restore base backup
pg_restore -h localhost -U postgres -d postgres -F t /tmp/base/

# Start PostgreSQL
systemctl start postgresql
```

### Step 3: Point-In-Time Recovery

```bash
# Recover to specific timestamp
pg_restore -h localhost -U postgres -d fisdb \
    --wal-segs dir=/tmp/wal/ \
    -T global \
    -e "SELECT pg_restore_to_time('2024-04-20 14:30:00 UTC')"
```

### Step 4: Verify Restoration

```sql
-- Check journal entry integrity
SELECT COUNT(*), MIN(posted_date), MAX(posted_date) FROM fis_journal_entry;

-- Verify hash chain
SELECT COUNT(*) FROM fis_journal_entry WHERE hash IS NULL;

-- Check for any gaps
SELECT posted_date, COUNT(*)
FROM fis_journal_entry
GROUP BY posted_date
HAVING COUNT(*) != (SELECT COUNT(*) FROM fis_journal_line WHERE journal_entry_id IN (
    SELECT id FROM fis_journal_entry WHERE posted_date = fis_journal_entry.posted_date
));
```

---

## Failover Procedures

### Database Failover (Primary → Replica)

```bash
#!/bin/bash
# Promote replica to primary

# On replica server
pg_ctl promote -D /var/lib/postgresql/data

# Update connection string in application
kubectl patch configmap fis-engine-config \
    --patch '{"data":{"DB_URL":"jdbc:postgresql://NEW_PRIMARY:5432/fisdb"}}'

# Restart application pods
kubectl rollout restart deployment fis-engine
```

### Application Failover

```bash
#!/bin/bash
# Switch traffic to backup region

# Update DNS
aws route53 change-resource-record-sets \
    --hosted-zone-id Z1234567890 \
    --change-batch file://dns-failover.json

# dns-failover.json content:
# {
#   "Changes": [{
#     "Action": "UPSERT",
#     "ResourceRecordSet": {
#       "Name": "api.fis-engine.example.com",
#       "Type": "A",
#       "TTL": 300,
#       "ResourceRecords": [{"Value": "10.0.1.50"}]
#     }
#   }]
# }
```

### Redis Failover

```bash
#!/bin/bash
# Promote Redis replica to primary

# On current replica
redis-cli SLAVEOF NO ONE

# Update application Redis URL
kubectl patch configmap fis-engine-config \
    --patch '{"data":{"REDIS_HOST":"new-redis-primary"}}'
```

---

## Post-Recovery Verification

### Health Check Script

```bash
#!/bin/bash
# Location: /opt/fis-engine/scripts/post-recovery-check.sh

set -e

echo "=== FIS-Engine Post-Recovery Verification ==="

# 1. Database connectivity
echo "1. Checking database connectivity..."
psql -h localhost -U fis_user -d fisdb -c "SELECT 1"

# 2. Journal entry count
echo "2. Checking journal entries..."
JOURNAL_COUNT=$(psql -h localhost -U fis_user -d fisdb -t -c "SELECT COUNT(*) FROM fis_journal_entry")
echo "   Total journal entries: $JOURNAL_COUNT"

# 3. Hash chain integrity
echo "3. Verifying hash chain..."
BROKEN_HASH=$(psql -h localhost -U fis_user -d fisdb -t -c "SELECT COUNT(*) FROM fis_journal_entry WHERE hash IS NULL")
if [ "$BROKEN_HASH" -gt 0 ]; then
    echo "   WARNING: Found $BROKEN_HASH entries with NULL hash"
else
    echo "   Hash chain intact"
fi

# 4. Redis connectivity
echo "4. Checking Redis..."
redis-cli ping

# 5. RabbitMQ
echo "5. Checking RabbitMQ..."
rabbitmqctl status | grep -E "memory|disk_space"

# 6. Application health
echo "6. Checking application health..."
curl -sf http://localhost:8080/actuator/health || exit 1

# 7. Test journal posting
echo "7. Running test journal posting..."
curl -X POST http://localhost:8080/v1/journal-entries \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
    -d '{
        "eventId": "test-recovery-'$(date +%s)'",
        "postedDate": "'$(date +%Y-%m-%d)'",
        "transactionCurrency": "USD",
        "createdBy": "recovery-test",
        "lines": [{
            "accountCode": "1100",
            "amountCents": 1000,
            "credit": false
        }, {
            "accountCode": "5100",
            "amountCents": 1000,
            "credit": true
        }]
    }' || exit 1

echo ""
echo "=== All checks passed ==="
```

---

## Emergency Contacts

| Role | Name | Contact |
|------|------|---------|
| DBA On-Call | TBD | oncall-dba@example.com |
| SRE On-Call | TBD | oncall-sre@example.com |
| Security | TBD | security@example.com |

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024-04-20 | FIS Team | Initial version |

Last Review: [Date]
Next Review: [Date + 90 days]