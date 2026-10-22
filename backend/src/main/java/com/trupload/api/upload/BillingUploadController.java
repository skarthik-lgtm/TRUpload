package com.trupload.api.upload;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trupload.service.upload.BillingUploadService;

@RestController
@RequestMapping("/api/uploads")
public class BillingUploadController {

    private final BillingUploadService billingUploadService;

    public BillingUploadController(BillingUploadService billingUploadService) {
        this.billingUploadService = billingUploadService;
    }

    @PostMapping
    public UploadResponse upload(
            @RequestBody UploadRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication is required to upload billings.");
        }

        String uploadedBy = authentication.getName();

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ADMIN"));

        String userRole = isAdmin ? "ADMIN" : "USER";

        return billingUploadService.upload(request, uploadedBy, userRole);
    }

    @GetMapping("/failed")
    public List<FailedUploadRecord> getFailedUploads() {
        return billingUploadService.findRecentFailures();
    }
}
