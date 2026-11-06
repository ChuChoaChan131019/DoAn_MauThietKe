package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.entity.NewsletterSubscription;
import com.senvia.doangiuaky.engagement.repository.NewsletterSubscriptionRepository;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class NewsletterSubscriptionService {

    private final NewsletterSubscriptionRepository repository;

    public NewsletterSubscriptionService(NewsletterSubscriptionRepository repository) {
        this.repository = repository;
    }

    public boolean subscribe(String email) {
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (repository.existsByEmail(normalized)) {
            return false;
        }
        repository.saveAndFlush(new NewsletterSubscription(normalized));
        return true;
    }
}
