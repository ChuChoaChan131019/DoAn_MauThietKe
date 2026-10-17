package com.senvia.doangiuaky.engagement.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationViewContractTest {

    @Test
    void notificationViewUsesRequestRefreshAndContainsNoRealtimeClient() throws IOException {
        String template = Files.readString(
                Path.of("src/main/resources/templates/engagement/notifications.html"),
                StandardCharsets.UTF_8);

        assertTrue(template.contains("th:each=\"item : ${notifications}\""));
        assertTrue(template.contains("@{/account/notifications/read-all}"));
        assertTrue(template.contains("method=\"post\""));
        assertFalse(template.contains("fetch("));
        assertFalse(template.contains("WebSocket"));
        assertFalse(template.contains("EventSource"));
        assertFalse(template.contains("setInterval"));
        assertFalse(template.contains("setTimeout"));
    }
}
