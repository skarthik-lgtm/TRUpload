package com.trupload.service.auth.login;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.trupload.dao.auth.login.LoginDao;
import com.trupload.model.auth.login.LoginRequest;
import com.trupload.model.auth.login.LoginResponse;

@Service
public class LoginServiceImpl implements LoginService {

    private final LoginDao loginDao;

    public LoginServiceImpl(LoginDao loginDao) {
        this.loginDao = loginDao;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String email = request.email().toLowerCase(Locale.ROOT);
        LoginDao.LoginUser user = loginDao.findByEmail(email)
                .filter(candidate -> candidate.password().equals(request.password()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Email or password is incorrect"));

        return new LoginResponse("Login successful", user.email());
    }
}