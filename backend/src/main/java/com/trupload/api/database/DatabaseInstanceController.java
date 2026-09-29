package com.trupload.api.database;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trupload.service.database.DatabaseInstanceService;

@RestController
@RequestMapping("/api/database")
public class DatabaseInstanceController {

    private final DatabaseInstanceService databaseInstanceService;

    public DatabaseInstanceController(DatabaseInstanceService databaseInstanceService) {
        this.databaseInstanceService = databaseInstanceService;
    }

    @GetMapping("/instances")
    public List<DatabaseInstanceResponse> getInstances() {
        return databaseInstanceService.findInstances();
    }
}
