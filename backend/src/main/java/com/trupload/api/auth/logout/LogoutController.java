package com.trupload.api.auth.logout;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trupload.security.JwtService;

@RestController
@RequestMapping("/api/auth/logout")
public class LogoutController {

    private final JwtService jwtService;

    public LogoutController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        jwtService.revokeToken(authorization.substring("Bearer ".length()));
        return ResponseEntity.noContent().build();
    }
}