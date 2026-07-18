#!/usr/bin/env sh
set -eu

REAL_PRE_COMPOSE_FILE="${REAL_PRE_COMPOSE_FILE:-${COMPOSE_FILE:-docker-compose.real-pre.yml}}"
REAL_PRE_COMPOSE_ENV="${REAL_PRE_COMPOSE_ENV:-${COMPOSE_ENV:-/opt/saas/env/.env.real-pre}}"
REAL_PRE_COMPOSE_PROJECT="${REAL_PRE_COMPOSE_PROJECT:-${COMPOSE_PROJECT_NAME:-saas-active}}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgres-real-pre}"

compose() {
  docker compose \
    --env-file "$REAL_PRE_COMPOSE_ENV" \
    --project-name "$REAL_PRE_COMPOSE_PROJECT" \
    -f "$REAL_PRE_COMPOSE_FILE" "$@"
}

echo "Checking real-pre core schema contract (read-only) ..."
missing="$(compose exec -T "$POSTGRES_SERVICE" sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1' <<'SQL'
WITH required(table_name, column_name) AS (
  VALUES
    ('colonel_activity', 'activity_status_synced_at'),
    ('colonelsettlement_order', 'channel_attribution_source'),
    ('colonelsettlement_order', 'channel_attribution_status'),
    ('colonelsettlement_order', 'recruiter_attribution_source'),
    ('colonelsettlement_order', 'recruiter_attribution_status'),
    ('pick_source_mapping', 'attribution_owner_type'),
    ('promotion_link', 'attribution_owner_type')
), missing_required AS (
  SELECT r.table_name || '.' || r.column_name AS missing
  FROM required r
  LEFT JOIN information_schema.columns c
    ON c.table_schema = 'public'
   AND c.table_name = r.table_name
   AND c.column_name = r.column_name
  WHERE c.column_name IS NULL
), order_partitions AS (
  SELECT child.relname AS table_name
  FROM pg_inherits
  JOIN pg_class child ON child.oid = pg_inherits.inhrelid
  JOIN pg_class parent ON parent.oid = pg_inherits.inhparent
  JOIN pg_namespace ns ON ns.oid = parent.relnamespace
  WHERE ns.nspname = 'public'
    AND parent.relname = 'colonelsettlement_order'
), missing_partition_columns AS (
  SELECT p.table_name || '.' || required_column.column_name AS missing
  FROM order_partitions p
  CROSS JOIN (VALUES
    ('channel_attribution_source'),
    ('channel_attribution_status'),
    ('recruiter_attribution_source'),
    ('recruiter_attribution_status')
  ) AS required_column(column_name)
  LEFT JOIN information_schema.columns c
    ON c.table_schema = 'public'
   AND c.table_name = p.table_name
   AND c.column_name = required_column.column_name
  WHERE c.column_name IS NULL
)
SELECT missing FROM missing_required
UNION ALL
SELECT missing FROM missing_partition_columns
ORDER BY 1;
SQL
)"

if [ -n "$missing" ]; then
  echo "ERROR: incompatible schema; missing contract entries:" >&2
  printf '%s\n' "$missing" >&2
  exit 1
fi

partition_count="$(compose exec -T "$POSTGRES_SERVICE" sh -lc \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_inherits JOIN pg_class parent ON parent.oid = pg_inherits.inhparent JOIN pg_namespace ns ON ns.oid = parent.relnamespace WHERE ns.nspname = '\''public'\'' AND parent.relname = '\''colonelsettlement_order'\'';"')"

echo "PASS: core schema compatible; order partitions checked: $partition_count"
