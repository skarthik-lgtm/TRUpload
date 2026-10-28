package com.trupload.api.access;

import java.util.List;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AccountAccessController {
    private final AccountAccessService accountAccessService;

    public AccountAccessController(AccountAccessService accountAccessService) {
        this.accountAccessService = accountAccessService;
    }

    @GetMapping("/api/account/requests")
    public List<AccountRequestResponse> getMyRequests(Authentication authentication) {
        return accountAccessService.findMyAccountRequests(authentication.getName());
    }

    @PostMapping("/api/warehouse-access-requests")
    public void createWarehouseAccessRequest(
            Authentication authentication,
            @Valid @RequestBody WarehouseAccessRequest request) {
        accountAccessService.createWarehouseAccessRequest(authentication.getName(), request);
    }

    @GetMapping("/api/warehouse-access-requests")
    public List<WarehouseAccessResponse> getMyWarehouseAccessRequests(Authentication authentication) {
        return accountAccessService.findWarehouseAccessRequests(authentication.getName(), false);
    }

    @GetMapping("/api/admin/account-requests")
    public List<AccountRequestResponse> getAccountRequests() {
        return accountAccessService.findAccountRequests();
    }

    @GetMapping("/api/admin/users")
    public List<AdminUserResponse> getUsers() {
        return accountAccessService.findUsers();
    }

    @GetMapping("/api/admin/audit-logs")
    public List<AuditLogResponse> getAuditLogs() {
        return accountAccessService.findAuditLogs();
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/admin/users/{id}/status")
    public void updateUserStatus(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody UserStatusRequest request) {
        accountAccessService.updateUserStatus(id, request.status(), authentication.getName());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/admin/users/{id}/role")
    public void updateUserRole(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody UserRoleRequest request) {
        accountAccessService.updateUserRole(id, request.role(), authentication.getName());
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/admin/users/{id}/warehouses")
    public void assignWarehouse(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody UserWarehouseRequest request) {
        accountAccessService.assignWarehouse(id, request.warehouseId(), authentication.getName());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/admin/users/{id}/warehouses/{warehouseId}")
    public void removeWarehouse(
            @PathVariable long id,
            @PathVariable long warehouseId,
            Authentication authentication) {
        accountAccessService.removeWarehouse(id, warehouseId, authentication.getName());
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/admin/users/{id}/warehouses/replace")
    public void replaceWarehouse(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody WarehouseReplacementRequest request) {
        accountAccessService.replaceWarehouse(id, request.currentWarehouseId(),
                request.requestedWarehouseId(), authentication.getName());
    }

    @PatchMapping("/api/admin/account-requests/{id}/account")
    public void decideAccount(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody DecisionRequest request) {
        accountAccessService.decideAccount(id, request.decision(), request.comments(), authentication.getName());
    }

    @PatchMapping("/api/admin/account-requests/warehouses/{id}")
    public void decideAccountWarehouse(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody DecisionRequest request) {
        accountAccessService.decideAccountWarehouse(id, request.decision(), request.comments(),
                authentication.getName());
    }

    @GetMapping("/api/admin/warehouse-access-requests")
    public List<WarehouseAccessResponse> getWarehouseAccessRequests() {
        return accountAccessService.findWarehouseAccessRequests(null, true);
    }

    @PatchMapping("/api/admin/warehouse-access-requests/{id}")
    public void decideWarehouseAccessRequest(
            @PathVariable long id,
            Authentication authentication,
            @Valid @RequestBody DecisionRequest request) {
        accountAccessService.decideWarehouseAccessRequest(
                id, request.decision(), request.comments(), authentication.getName());
    }
}