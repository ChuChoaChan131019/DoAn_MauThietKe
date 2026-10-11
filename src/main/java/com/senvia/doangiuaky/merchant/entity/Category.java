package com.senvia.doangiuaky.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean active;

    protected Category() {
    }

    private Category(String name, String description) {
        updateDetails(name, description);
        this.active = true;
    }

    public static Category create(String name, String description) {
        return new Category(name, description);
    }

    public void updateDetails(String name, String description) {
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
    }

    public void hide() {
        this.active = false;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 100) {
            throw new IllegalArgumentException("Category name must not exceed 100 characters");
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
