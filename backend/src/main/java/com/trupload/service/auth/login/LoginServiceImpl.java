package com.trupload.service.auth.login;

import java.util.Locale;

import jakarta.transaction.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.trupload.dao.auth.login.LoginDao;
import com.trupload.model.auth.login.LoginRequest;
import com.trupload.model.auth.login.LoginResponse;
import com.trupload.model.auth.signup.SignupUser;
import com.trupload.security.JwtService;

@Service
public class LoginServiceImpl implements LoginService {

    private final LoginDao loginDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginServiceImpl(LoginDao loginDao, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.loginDao = loginDao;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = request.email().trim().toLowerCase(Locale.ROOT);
        SignupUser user = loginDao.findByLoginIdentifier(identifier)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Login account not found"));

        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Login account is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Password verification failed");
        }
        user.markLoginNow();
        Long roleId = loginDao.findRoleIdByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "No role is assigned to this user"));

        return new LoginResponse(
                "Login successful",
                user.email(),
                roleId,
                jwtService.generateToken(user, roleId));
    }
}