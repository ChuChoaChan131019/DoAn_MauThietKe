package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.NewsletterSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsletterSubscriptionRepository extends JpaRepository<NewsletterSubscription, Long> {
    boolean existsByEmail(String email);
}
