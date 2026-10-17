package com.senvia.doangiuaky.ordering.api;

import java.time.Instant;

public record OrderHistoryEntry(Long id, String oldStatus, String newStatus, String statusLabel, Long changedBy, String note, Instant changedAt, String displayTime) {
}