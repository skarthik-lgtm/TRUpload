package com.trupload.dao.auth.login;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public class LoginDaoImpl implements LoginDao {

    private final Map<String, LoginUser> users = Map.of(
            "demo@trupload.com", new LoginUser("demo@trupload.com", "TRUpload123!"));

    @Override
    public Optional<LoginUser> findByEmail(String email) {
        return Optional.ofNullable(users.get(email.toLowerCase(Locale.ROOT)));
    }
}