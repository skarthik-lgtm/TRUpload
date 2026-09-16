package com.trupload.service.auth.signup;

import com.trupload.model.auth.signup.SignupRequest;
import com.trupload.model.auth.signup.SignupResponse;

public interface SignupService {

    SignupResponse signup(SignupRequest request);
}
