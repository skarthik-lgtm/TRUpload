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
                .filter(candidate -> "ACTIVE".equalsIgnoreCase(candidate.getStatus()))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));
        user.markLoginNow();

        return new LoginResponse("Login successful", user.email(), jwtService.generateToken(user));
    }
}