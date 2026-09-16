package com.trupload.dao.auth.login;

import java.util.Optional;

public interface LoginDao {

    Optional<LoginUser> findByEmail(String email);

    record LoginUser(String email, String password) {
    }
}
