CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    receiver_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    reference_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users(id),
    CONSTRAINT uk_notifications_event_receiver UNIQUE (event_id, receiver_id)
);
