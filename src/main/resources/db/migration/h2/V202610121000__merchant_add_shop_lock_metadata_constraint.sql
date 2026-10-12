ALTER TABLE shops ADD CONSTRAINT ck_shops_lock_metadata CHECK (
    (status = 'LOCKED'
        AND lock_reason IS NOT NULL
        AND LENGTH(TRIM(lock_reason)) > 0
        AND locked_by IS NOT NULL
        AND locked_at IS NOT NULL)
    OR
    (status <> 'LOCKED'
        AND lock_reason IS NULL
        AND locked_by IS NULL
        AND locked_at IS NULL)
);
