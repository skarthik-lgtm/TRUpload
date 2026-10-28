package com.trupload.model.auth.signup;

import java.time.LocalDateTime;

public class SignupUser {

    private Long id;

    private String username;

    private String email;

    private String passwordHash;

    private String firstName;

    private String lastName;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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

    public SignupUser(Long id, String username, String email, String firstName, String lastName,
            String passwordHash, String status, LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.lastLoginAt = lastLoginAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
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
