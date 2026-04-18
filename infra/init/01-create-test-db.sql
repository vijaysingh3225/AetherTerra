-- Creates the test database alongside the main dev database.
-- This script runs automatically on first container start (empty volume).
-- For existing volumes: docker exec aetherterra-postgres psql -U aetherterra -c "CREATE DATABASE aetherterra_test;"
SELECT 'CREATE DATABASE aetherterra_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'aetherterra_test')\gexec
