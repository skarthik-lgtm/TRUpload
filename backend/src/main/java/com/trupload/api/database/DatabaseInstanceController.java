package com.trupload.api.database;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trupload.service.database.DatabaseInstanceService;

@RestController
@RequestMapping("/api/database")
public class DatabaseInstanceController {

    private final DatabaseInstanceService databaseInstanceService;

    public DatabaseInstanceController(
            DatabaseInstanceService databaseInstanceService) {

        this.databaseInstanceService =
                databaseInstanceService;
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
    public List<DatabaseInstanceResponse> getInstances() {

        return databaseInstanceService.findInstances();
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
            @PathVariable String databaseName) {

        return databaseInstanceService.findRoutes(databaseName);
    }
}