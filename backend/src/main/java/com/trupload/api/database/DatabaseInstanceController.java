package com.trupload.api.database;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.trupload.api.access.AccountAccessService;

import com.trupload.service.database.DatabaseInstanceService;

@RestController
@RequestMapping("/api/database")
public class DatabaseInstanceController {

    private final DatabaseInstanceService databaseInstanceService;
    private final AccountAccessService accountAccessService;

    public DatabaseInstanceController(
            DatabaseInstanceService databaseInstanceService,
            AccountAccessService accountAccessService) {

        this.databaseInstanceService = databaseInstanceService;
        this.accountAccessService = accountAccessService;
    }

    /*
     * ============================================================
     * GET ALL DATABASES
     * ============================================================
     *
     * GET /api/database/instances
     *
     * Returns all CSTR databases and their complete route data.
     */
    @GetMapping("/instances")
    public List<DatabaseInstanceResponse> getInstances(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return admin
                ? databaseInstanceService.findInstances()
                : databaseInstanceService.findInstances(
                        accountAccessService.permittedDatabases(authentication.getName()),
                        accountAccessService.permittedFacilityIds(authentication.getName()));
    }

    /*
     * ============================================================
     * GET ONE DATABASE
     * ============================================================
     *
     * GET /api/database/{databaseName}/routes
     *
     * Example:
     *
     * GET /api/database/CSTRRB_QA/routes
     */
    @GetMapping("/{databaseName}/routes")
    public DatabaseInstanceResponse getRoutes(
            @PathVariable String databaseName,
            Authentication authentication) {

        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin && !accountAccessService.permittedDatabases(authentication.getName()).contains(databaseName)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Warehouse access is not assigned to this account.");
        }

        DatabaseInstanceResponse result = databaseInstanceService.findRoutes(databaseName);
        if (admin) {
            return result;
        }
        java.util.Set<String> facilityIds = accountAccessService.permittedFacilityIds(authentication.getName());
        return new DatabaseInstanceResponse(
                result.databaseName(),
                result.routeGroups().stream()
                        .filter(group -> group.orderFacilities().stream()
                                .anyMatch(facility -> facilityIds.contains(facility.sourceFacilityId())))
                        .toList(),
                result.lastCheckDateTime());
    }
}