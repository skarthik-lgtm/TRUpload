package com.trupload.api.upload;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trupload.service.upload.BillingUploadService;
import com.trupload.api.access.AccountAccessService;

@RestController
@RequestMapping("/api/uploads")
public class BillingUploadController {

    private final BillingUploadService billingUploadService;
    private final AccountAccessService accountAccessService;

    @Autowired
    public BillingUploadController(BillingUploadService billingUploadService,
            AccountAccessService accountAccessService) {
        this.billingUploadService = billingUploadService;
        this.accountAccessService = accountAccessService;
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

        if (!isAdmin) {
            boolean hasUnauthorizedSelection = request == null || request.selections() == null
                    || request.selections().stream().anyMatch(selection -> selection == null
                            || !accountAccessService.hasGroupAccess(
                                    uploadedBy, selection.databaseName(), selection.routeGroupId()));
            if (hasUnauthorizedSelection) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.FORBIDDEN,
                        "One or more selected warehouses are not assigned to this account.");
            }
        }

        return billingUploadService.upload(request, uploadedBy, userRole);
    }

    @GetMapping("/failed")
    public List<FailedUploadRecord> getFailedUploads(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return billingUploadService.findRecentFailures(authentication.getName(), admin);
    }

    @GetMapping("/successful")
    public List<SuccessfulUploadRecord> getSuccessfulUploads(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return billingUploadService.findRecentSuccessfulUploads(authentication.getName(), admin);
    }

}
