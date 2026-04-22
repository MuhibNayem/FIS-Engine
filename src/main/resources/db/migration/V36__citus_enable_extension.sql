-- V36__citus_enable_extension.sql
-- Enable Citus extension on coordinator and workers
-- This must run as superuser

-- Only run if Citus extension is not already enabled
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'citus') THEN
        CREATE EXTENSION IF NOT EXISTS citus;
        RAISE NOTICE 'Citus extension enabled';
    ELSE
        RAISE NOTICE 'Citus extension already enabled';
    END IF;
END;
$$;

-- Verify Citus is properly configured
DO $$
BEGIN
    PERFORM citus_version();
    RAISE NOTICE 'Citus version: %', citus_version();
EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'Citus not properly configured: %', SQLERRM;
END;
$$;