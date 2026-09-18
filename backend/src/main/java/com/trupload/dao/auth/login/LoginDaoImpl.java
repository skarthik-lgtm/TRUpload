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
    public Optional<SignupUser> findByLoginIdentifier(String identifier) {
        return entityManager.createQuery(
                        "select user from SignupUser user where lower(user.email) = :identifier "
                                + "or lower(user.username) = :identifier",
                        SignupUser.class)
                .setParameter("identifier", identifier.toLowerCase(Locale.ROOT))
                .getResultStream()
                .findFirst();
    }
}