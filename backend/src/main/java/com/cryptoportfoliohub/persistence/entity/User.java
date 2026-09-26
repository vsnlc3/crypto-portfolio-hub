package com.cryptoportfoliohub.persistence.entity;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "users", uniqueConstraints =
        @UniqueConstraint(name = "uq_users_google_subject", columnNames = "google_subject"))
public class User extends UpdatedEntity {

    @Column(name = "google_subject", nullable = false, length = 255)
    private String googleSubject;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "avatar_url", columnDefinition = "text")
    private String avatarUrl;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    public User(String googleSubject, String email, String displayName, String avatarUrl) {
        this.googleSubject = googleSubject;
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
