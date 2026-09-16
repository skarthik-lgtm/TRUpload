package com.trupload.service.auth.login;

import com.trupload.model.auth.login.LoginRequest;
import com.trupload.model.auth.login.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest request);
}
