#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CITUS_VERSION="${CITUS_VERSION:-14.0.0}"
PG_VERSION="${PG_VERSION:-16}"

echo "=== Citus Infrastructure Setup Script ==="
echo "Citus Version: $CITUS_VERSION"
echo "PostgreSQL Version: $PG_VERSION"
echo ""

check_command() {
    if ! command -v "$1" &> /dev/null; then
        echo "ERROR: $1 not found. Please install $1 first."
        return 1
    fi
}

check_command "psql"
check_command "pg_config"

setup_citus_extension() {
    local host="$1"
    local port="$2"
    local dbname="$3"
    local user="$4"
    local pgdata="$5"

    echo "=== Setting up Citus on $host:$port ==="

    if [ ! -f "${pgdata}/postgresql.conf.backup" ]; then
        cp "${pgdata}/postgresql.conf" "${pgdata}/postgresql.conf.backup" 2>/dev/null || true
    fi

    echo "shared_preload_libraries = 'citus'" >> "${pgdata}/postgresql.conf"
    echo "citus.shard_count = 32" >> "${pgdata}/postgresql.conf"
    echo "citus.shard_replication_factor = 1" >> "${pgdata}/postgresql.conf"

    echo "Citus configuration added to postgresql.conf"
}

create_coordinator() {
    local host="$1"
    local port="$2"
    local dbname="$3"
    local user="$4"
    local coordinator_dir="$5"

    echo "=== Creating Coordinator Node ==="

    mkdir -p "${coordinator_dir}"
    initdb -D "${coordinator_dir}" --no-locale --encoding=UTF-8 >/dev/null 2>&1 || true

    setup_citus_extension "$host" "$port" "$dbname" "$user" "$coordinator_dir"

    if [ -f "${coordinator_dir}/postgresql.conf.backup" ]; then
        cp "${coordinator_dir}/postgresql.conf.backup" "${coordinator_dir}/postgresql.conf" 2>/dev/null || true
        echo "shared_preload_libraries = 'citus'" >> "${coordinator_dir}/postgresql.conf"
        echo "citus.shard_count = 32" >> "${coordinator_dir}/postgresql.conf"
        echo "citus.shard_replication_factor = 1" >> "${coordinator_dir}/postgresql.conf"
    fi

    cat >> "${coordinator_dir}/postgresql.conf" <<EOF
listen_addresses = '*'
port = ${port}
max_connections = 200
shared_buffers = 256MB
work_mem = 16MB
maintenance_work_mem = 128MB
effective_cache_size = 1GB
citus.shard_count = 32
citus.shard_replication_factor = 1
EOF

    pg_ctl -D "${coordinator_dir}" start -l "${coordinator_dir}/coordinator.log" 2>/dev/null || true
    sleep 3

    psql -h "$host" -p "$port" -U "$user" -d postgres -c "CREATE EXTENSION IF NOT EXISTS citus;"

    echo "Coordinator created at $host:$port"
}

add_worker_node() {
    local coordinator_host="$1"
    local coordinator_port="$2"
    local coordinator_user="$3"
    local worker_host="$4"
    local worker_port="$5"

    echo "=== Adding Worker Node $worker_host:$worker_port ==="

    psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d postgres -c \
        "SELECT citus_add_node('$worker_host', $worker_port);"

    echo "Worker $worker_host:$worker_port added to cluster"
}

create_distributed_table() {
    local coordinator_host="$1"
    local coordinator_port="$2"
    local coordinator_user="$3"
    local dbname="$4"
    local table_name="$5"
    local distribution_column="$6"
    local shard_key_type="${7:-text}"

    echo "=== Creating Distributed Table: $table_name ($distribution_column) ==="

    case "$shard_key_type" in
        int)
            psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d "$dbname" -c \
                "SELECT create_distributed_table('$table_name', '$distribution_column', 'hash');"
            ;;
        text)
            psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d "$dbname" -c \
                "SELECT create_distributed_table('$table_name', '$distribution_column', 'hash');"
            ;;
        append)
            psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d "$dbname" -c \
                "SELECT create_distributed_table('$table_name', '$distribution_column', 'append');"
            ;;
        *)
            echo "Unknown shard key type: $shard_key_type"
            return 1
            ;;
    esac

    echo "Table $table_name distributed"
}

create_reference_table() {
    local coordinator_host="$1"
    local coordinator_port="$2"
    local coordinator_user="$3"
    local dbname="$4"
    local table_name="$5"

    echo "=== Creating Reference Table: $table_name ==="

    psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d "$dbname" -c \
        "SELECT create_reference_table('$table_name');"

    echo "Reference table $table_name created"
}

verify_cluster() {
    local coordinator_host="$1"
    local coordinator_port="$2"
    local coordinator_user="$3"

    echo "=== Verifying Citus Cluster ==="

    psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d postgres -c \
        "SELECT * FROM citus_get_active_worker_nodes();"

    psql -h "$coordinator_host" -p "$coordinator_port" -U "$coordinator_user" -d postgres -c \
        "SELECT citus_version();"

    echo "Cluster verification complete"
}

if [ "${1:-}" == "setup" ]; then
    COORDINATOR_HOST="${COORDINATOR_HOST:-localhost}"
    COORDINATOR_PORT="${COORDINATOR_PORT:-5432}"
    COORDINATOR_USER="${COORDINATOR_USER:-$USER}"
    DBNAME="${DBNAME:-fisdb}"
    COORDINATOR_DIR="${COORDINATOR_DIR:-/tmp/citus/coordinator}"

    create_coordinator "$COORDINATOR_HOST" "$COORDINATOR_PORT" "$DBNAME" "$COORDINATOR_USER" "$COORDINATOR_DIR"

    echo ""
    echo "=== Citus Coordinator Setup Complete ==="
    echo "Run the following to enable distributed tables:"
    echo ""
    echo "  psql -h $COORDINATOR_HOST -p $COORDINATOR_PORT -U $COORDINATOR_USER -d $DBNAME"
    echo "  SELECT create_distributed_table('fis_journal_entry', 'tenant_id', 'hash');"
    echo "  SELECT create_distributed_table('fis_journal_line', 'journal_entry_id', 'hash');"
    echo "  SELECT create_reference_table('fis_account');"
    echo ""
fi

if [ "${1:-}" == "verify" ]; then
    COORDINATOR_HOST="${COORDINATOR_HOST:-localhost}"
    COORDINATOR_PORT="${COORDINATOR_PORT:-5432}"
    COORDINATOR_USER="${COORDINATOR_USER:-$USER}"

    verify_cluster "$COORDINATOR_HOST" "$COORDINATOR_PORT" "$COORDINATOR_USER"
fi

echo ""
echo "=== Setup Complete ==="