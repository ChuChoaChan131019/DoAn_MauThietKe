package com.senvia.doangiuaky.engagement.dto;

import java.time.Instant;

public record ReviewListItem(
        int rating,
        String comment,
        String reviewerName,
        Instant createdAt) {
}
