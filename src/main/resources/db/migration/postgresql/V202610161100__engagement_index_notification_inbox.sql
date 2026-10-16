CREATE INDEX idx_notifications_receiver_read_created
    ON notifications (receiver_id, is_read, created_at DESC);
