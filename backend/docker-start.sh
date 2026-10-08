#!/bin/sh
# Starts PocketBase on the port the host gives us (Railway sets PORT).
# If HISAB_ADMIN_EMAIL and HISAB_ADMIN_PASSWORD are set, that admin account
# is created (or its password reset) first, so the dashboard at /_/ works.
set -e
if [ -n "$HISAB_ADMIN_EMAIL" ] && [ -n "$HISAB_ADMIN_PASSWORD" ]; then
  pocketbase superuser upsert "$HISAB_ADMIN_EMAIL" "$HISAB_ADMIN_PASSWORD" \
    --dir=/pb_data --migrationsDir=/pb_migrations >/dev/null
fi
exec pocketbase serve --http="0.0.0.0:${PORT:-8090}" --dir=/pb_data \
  --hooksDir=/pb_hooks --migrationsDir=/pb_migrations --hooksWatch=false
