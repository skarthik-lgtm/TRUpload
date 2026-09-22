package com.trupload.dao.auth.login;

import java.util.Optional;

import com.trupload.model.auth.signup.SignupUser;

public interface LoginDao {

    Optional<SignupUser> findByLoginIdentifier(String identifier);

    Optional<Long> findRoleIdByUserId(Long userId);
}
