package com.trupload.model.auth.signup;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", schema = "app")
public class SignupUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected SignupUser() {
    }

    public SignupUser(String username, String email, String firstName, String lastName,
            String passwordHash, LocalDateTime createdAt) {
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String email() {
        return email;
    }

    public String getPassword() {
        return passwordHash;
    }

    public String getStatus() {
        return status;
    }

    public void markLoginNow() {
        lastLoginAt = LocalDateTime.now();
        updatedAt = lastLoginAt;
    }

    public LocalDateTime getCreatedDate() {
        return createdAt;
    }

    public LocalDateTime getLastLoginDate() {
        return lastLoginAt;
    }
}
