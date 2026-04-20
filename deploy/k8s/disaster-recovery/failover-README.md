# FIS-Engine Disaster Recovery Runbook

## Overview

This runbook provides step-by-step procedures for recovering the FIS-Engine platform from various disaster scenarios.

### Recovery Objectives

| Metric | Target | Description |
|--------|--------|-------------|
| **RPO** | < 5 minutes | Maximum data loss (Recovery Point Objective) |
| **RTO** | < 1 hour | Time to restore service (Recovery Time Objective) |
| **Availability** | 99.99% | Monthly uptime target |

### Backup Schedule

| Component | Frequency | Retention | Storage |
|-----------|-----------|-----------|---------|
| PostgreSQL | Daily at 02:00 UTC | 30 days | S3 (Standard-IA, AES256 encrypted) |
| Redis | In-memory replication | N/A | 2 replicas across AZs |
| RabbitMQ | Quorum queues (persistent) | N/A | 3-node cluster with persistent volumes |
| K8s Manifests | Git (version control) | Unlimited | GitHub repository |

---

## Scenario 1: PostgreSQL Database Failure

### Symptoms
- Application logs show `Connection refused` or `database does not exist`
- Health endpoint returns `DOWN` for `db` indicator
- `kubectl get pods -n fis-production` shows PostgreSQL pods in `CrashLoopBackOff`

### Recovery Steps

#### Step 1: Assess the damage

```bash
# Check PostgreSQL pod status
kubectl get pods -n fis-production -l app=postgres

# Check pod events
kubectl describe pod postgres-0 -n fis-production

# Check PVC status
kubectl get pvc -n fis-production -l app=postgres

# Check PostgreSQL logs
kubectl logs postgres-0 -n fis-production --tail=100
```

#### Step 2: If PVC is intact (pod crash, not data loss)

```bash
# Delete the failed pod (StatefulSet will recreate it)
kubectl delete pod postgres-0 -n fis-production

# Monitor recreation
kubectl get pods -n fis-production -l app=postgres -w

# Verify data integrity
kubectl exec -it postgres-0 -n fis-production -- \
  pg_isready -U fis -d fis
```

#### Step 3: If PVC is corrupted (data loss)

```bash
# 1. Scale down the application to prevent writes
kubectl scale deployment fis-process --replicas=0 -n fis-production

# 2. Delete the corrupted StatefulSet and PVC
kubectl delete statefulset postgres -n fis-production
kubectl delete pvc postgres-data-postgres-0 -n fis-production

# 3. List available backups
aws s3 ls s3://fis-engine-backups/postgresql/ --region us-east-1

# 4. Edit the restore job with the latest backup file
# Edit: deploy/k8s/disaster-recovery/restore-job.yaml
# Set BACKUP_FILE to the latest backup (e.g., fis-db-backup-20260412T020000Z.sql.gz)

# 5. Deploy PostgreSQL StatefulSet (empty)
kubectl apply -f deploy/k8s/production/postgres-statefulset.yaml

# Wait for PostgreSQL to be ready
kubectl wait --for=condition=ready pod/postgres-0 -n fis-production --timeout=300s

# 6. Run the restore job
kubectl apply -f deploy/k8s/disaster-recovery/restore-job.yaml

# 7. Monitor restore progress
kubectl logs -f job/postgres-restore -n fis-production

# 8. Verify restore
kubectl exec -it postgres-0 -n fis-production -- \
  psql -U fis -d fis -c "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public';"

# 9. Clean up restore job
kubectl delete job postgres-restore -n fis-production

# 10. Scale application back up
kubectl scale deployment fis-process --replicas=3 -n fis-production
```

---

## Scenario 2: Redis Failure

### Symptoms
- Idempotency checks falling back to PostgreSQL
- Increased latency on journal posting
- `Connection refused` errors to Redis

### Recovery Steps

#### Step 1: Check Redis cluster status

```bash
# Check Redis pods
kubectl get pods -n fis-production -l app=redis

# Check primary
kubectl exec -it redis-primary-0 -n fis-production -- redis-cli -a $REDIS_PASSWORD ping

# Check replication
kubectl exec -it redis-primary-0 -n fis-production -- \
  redis-cli -a $REDIS_PASSWORD info replication
```

#### Step 2: Restart failed replica

```bash
# If a replica is down
kubectl delete pod redis-replica-0 -n fis-production

# If primary is down (replica will be promoted)
kubectl delete pod redis-primary-0 -n fis-production

# Monitor recreation
kubectl get pods -n fis-production -l app=redis -w
```

#### Step 3: If data is lost (PVC corruption)

```bash
# Delete and recreate Redis StatefulSets
kubectl delete statefulset redis-primary redis-replica -n fis-production
kubectl delete pvc -l app=redis -n fis-production

# Recreate
kubectl apply -f deploy/k8s/production/redis-statefulset.yaml

# Note: Redis data is ephemeral for idempotency cache.
# The application will rebuild the cache from PostgreSQL fallback.
```

---

## Scenario 3: RabbitMQ Cluster Failure

### Symptoms
- Messages not being processed
- Outbox events not being published
- Application logs show AMQP connection errors

### Recovery Steps

#### Step 1: Check RabbitMQ cluster status

```bash
kubectl get pods -n fis-production -l app=rabbitmq
kubectl exec -it rabbitmq-0 -n fis-production -- rabbitmq-diagnostics check_running
kubectl exec -it rabbitmq-0 -n fis-production -- rabbitmq-diagnostics check_if_node_is_quorum_critical
```

#### Step 2: Restart failed node

```bash
kubectl delete pod rabbitmq-0 -n fis-production
kubectl wait --for=condition=ready pod/rabbitmq-0 -n fis-production --timeout=300s
```

#### Step 3: If cluster is partitioned

```bash
# Force stop all nodes
for i in 0 1 2; do
  kubectl exec rabbitmq-$i -n fis-production -- rabbitmqctl stop_app
done

# Restart in order
for i in 0 1 2; do
  kubectl exec rabbitmq-$i -n fis-production -- rabbitmqctl start_app
  sleep 10
done

# Verify cluster status
kubectl exec rabbitmq-0 -n fis-production -- rabbitmqctl cluster_status
```

#### Step 4: If data is lost

```bash
# Delete StatefulSet and PVCs
kubectl delete statefulset rabbitmq -n fis-production
kubectl delete pvc -l app=rabbitmq -n fis-production

# Recreate
kubectl apply -f deploy/k8s/production/rabbitmq-statefulset.yaml

# Note: Quorum queues will replicate data across new nodes.
# Unprocessed messages in classic queues will be lost.
# The outbox pattern ensures eventual consistency.
```

---

## Scenario 4: Complete Region Failure

### Symptoms
- Entire region is unavailable
- All pods, services, and data in the region are lost

### Recovery Steps

#### Step 1: Activate DR region

```bash
# Set context to DR region cluster
kubectl config use-context fis-dr-cluster

# Create namespace if not exists
kubectl apply -f deploy/k8s/production/namespace.yaml
```

#### Step 2: Restore PostgreSQL from latest S3 backup

```bash
# List backups
aws s3 ls s3://fis-engine-backups/postgresql/ --region us-east-1

# Deploy PostgreSQL
kubectl apply -f deploy/k8s/production/postgres-statefulset.yaml

# Wait for readiness
kubectl wait --for=condition=ready pod/postgres-0 -n fis-production --timeout=300s

# Edit and run restore job
# Edit BACKUP_FILE in restore-job.yaml
kubectl apply -f deploy/k8s/disaster-recovery/restore-job.yaml
kubectl logs -f job/postgres-restore -n fis-production
```

#### Step 3: Deploy application

```bash
# Deploy all production manifests
kubectl apply -f deploy/k8s/production/configmap.yaml
kubectl apply -f deploy/k8s/production/secret.yaml
kubectl apply -f deploy/k8s/production/deployment.yaml
kubectl apply -f deploy/k8s/production/service.yaml
kubectl apply -f deploy/k8s/production/ingress.yaml

# Deploy supporting services
kubectl apply -f deploy/k8s/production/redis-statefulset.yaml
kubectl apply -f deploy/k8s/production/rabbitmq-statefulset.yaml
```

#### Step 4: Verify

```bash
# Check all pods are running
kubectl get pods -n fis-production

# Check health endpoint
kubectl exec -it $(kubectl get pod -l app=fis-process -n fis-production -o jsonpath='{.items[0].metadata.name}') \
  -n fis-production -- \
  curl -s http://localhost:8080/actuator/health | jq

# Run smoke test
curl -f https://${FIS_DOMAIN}/actuator/health
```

#### Step 5: Update DNS

```bash
# Update Route53 / DNS to point to DR region load balancer
# This depends on your DNS provider and setup
```

---

## Scenario 5: Accidental Data Deletion

### Symptoms
- User reports missing journal entries or accounts
- Database queries return fewer rows than expected

### Recovery Steps

#### Step 1: Stop all writes

```bash
# Scale down application
kubectl scale deployment fis-process --replicas=0 -n fis-production
```

#### Step 2: Identify the latest backup before deletion

```bash
aws s3 ls s3://fis-engine-backups/postgresql/ --region us-east-1
# Find the backup just before the deletion event
```

#### Step 3: Restore to a temporary database

```bash
# Create a temporary PostgreSQL instance
kubectl run postgres-temp-restore -n fis-production \
  --image=postgres:17-alpine \
  --env=POSTGRES_PASSWORD=$(kubectl get secret postgres-secret -n fis-production -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 -d) \
  --env=POSTGRES_DB=fis_restore

# Download and restore backup to temporary database
# (Use a modified restore-job.yaml pointing to postgres-temp-restore)
```

#### Step 4: Extract and re-insert missing data

```bash
# Connect to temporary database
kubectl exec -it postgres-temp-restore -n fis-production -- \
  psql -U fis -d fis_restore

# Export missing data
kubectl exec -it postgres-temp-restore -n fis-production -- \
  pg_dump -U fis -d fis_restore --table=journal_entry --table=journal_line \
  --format=custom > /tmp/missing_data.dump

# Import to production database
kubectl cp /tmp/missing_data.dump postgres-0:/tmp/ -n fis-production
kubectl exec -it postgres-0 -n fis-production -- \
  pg_restore -U fis -d fis --no-acl --no-owner /tmp/missing_data.dump
```

#### Step 5: Scale application back up

```bash
kubectl scale deployment fis-process --replicas=3 -n fis-production
```

#### Step 6: Cleanup

```bash
kubectl delete pod postgres-temp-restore -n fis-production
```

---

## Preventive Measures

### 1. Automated Backup Verification

```bash
# CronJob to verify backup integrity daily
# Add to deploy/k8s/disaster-recovery/backup-verify-cronjob.yaml
```

### 2. Regular DR Drills

- **Monthly**: Test backup restore in staging
- **Quarterly**: Full region failover drill
- **Annually**: Chaos engineering test (use LitmusChaos or similar)

### 3. Monitoring Alerts

| Alert | Condition | Runbook |
|-------|-----------|---------|
| PostgreSQL down | Pod not ready for > 5min | Scenario 1 |
| Backup failed | CronJob status = Failed | Check CronJob logs |
| Replication lag > 5min | `pg_stat_replication` check | Check network / primary |
| Disk usage > 80% | PVC usage alert | Scale storage |
| RabbitMQ partition | `check_if_node_is_quorum_critical` | Scenario 3 |
| Redis memory > 90% | Redis INFO memory | Scale Redis |

### 4. Backup Encryption

All backups are encrypted at rest using AWS S3 SSE-AES256:
```bash
aws s3 cp backup.sql.gz s3://fis-engine-backups/postgresql/ \
  --server-side-encryption AES256
```

### 5. Access Control

- Only the `postgres-backup-sa` ServiceAccount can trigger backups
- S3 bucket has a bucket policy restricting access to the backup IAM role
- Restore jobs require manual intervention (no automated restores)

---

## Contact Information

| Role | Contact | Escalation |
|------|---------|------------|
| On-call SRE | PagerDuty: FIS-Engine | Primary |
| Database Admin | dba-team@company.com | Secondary |
| Platform Lead | platform-lead@company.com | Tertiary |

---

## Revision History

| Date | Author | Changes |
|------|--------|---------|
| 2026-04-13 | SRE Team | Initial version |
