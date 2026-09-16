package com.trupload.dao.auth.signup;

import com.trupload.model.auth.signup.SignupUser;

public interface SignupDao {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    SignupUser save(SignupUser user);
}
