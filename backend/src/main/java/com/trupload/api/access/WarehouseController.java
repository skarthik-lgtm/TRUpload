package com.trupload.api.access;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {
    private final AccountAccessService accountAccessService;

    public WarehouseController(AccountAccessService accountAccessService) {
        this.accountAccessService = accountAccessService;
    }

    @GetMapping
    public List<WarehouseResponse> findWarehouses() {
        return accountAccessService.findWarehouses();
    }

    @GetMapping("/mine")
    public List<WarehouseResponse> findMyWarehouses(Authentication authentication) {
        return accountAccessService.findMyWarehouses(authentication.getName());
    }
}