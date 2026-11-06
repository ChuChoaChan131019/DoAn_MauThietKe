package com.senvia.doangiuaky.engagement.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NewsletterControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousRegistrationNormalizesEmailAndRejectsDuplicate() throws Exception {
        String email = "newsletter-" + UUID.randomUUID() + "@example.test";
        try {
            MvcResult result = mockMvc.perform(post("/newsletter/subscribe").with(csrf())
                            .param("email", "  " + email.toUpperCase() + "  "))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/#newsletter"))
                    .andExpect(flash().attributeExists("newsletterSuccess"))
                    .andReturn();

            mockMvc.perform(get("/").flashAttrs(result.getFlashMap()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Đã ghi nhận email đăng ký nhận bản tin.")))
                    .andExpect(content().string(containsString("action=\"/newsletter/subscribe\"")));

            mockMvc.perform(post("/newsletter/subscribe").with(csrf()).param("email", email))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("newsletterError"));

            assertEquals(1L, countByEmail(email));
        } finally {
            jdbcTemplate.update("DELETE FROM newsletter_subscriptions WHERE email = ?", email);
        }
    }

    @Test
    void invalidEmailAndMissingCsrfDoNotCreateSubscription() throws Exception {
        mockMvc.perform(post("/newsletter/subscribe").with(csrf()).param("email", "invalid-address"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("newsletterError"));
        mockMvc.perform(post("/newsletter/subscribe").with(csrf()).param("email", " "))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("newsletterError"));
        mockMvc.perform(post("/newsletter/subscribe").param("email", "missing-csrf@example.test"))
                .andExpect(status().isForbidden());

        assertEquals(0L, countByEmail("invalid-address"));
        assertEquals(0L, countByEmail("missing-csrf@example.test"));
    }

    private long countByEmail(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM newsletter_subscriptions WHERE email = ?", Long.class, email);
    }
}
