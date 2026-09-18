package com.trupload.dao.auth.login;

import java.util.Locale;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import com.trupload.model.auth.signup.SignupUser;

@Repository
public class LoginDaoImpl implements LoginDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<SignupUser> findByEmail(String email) {
        return entityManager.createQuery(
                        "select user from SignupUser user where lower(user.email) = :email",
                        SignupUser.class)
                .setParameter("email", email.toLowerCase(Locale.ROOT))
                .getResultStream()
                .findFirst();
    }
}