package com.trupload.dao.auth.signup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;

import com.trupload.model.auth.signup.SignupUser;

@Repository
public class SignupDaoImpl implements SignupDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean existsByEmail(String email) {
        return countBy("email", email) > 0;
    }

    @Override
    public boolean existsByUsername(String username) {
        return countBy("username", username) > 0;
    }

    @Override
    public SignupUser save(SignupUser user) {
        entityManager.persist(user);
        return user;
    }

    @Override
    public void assignRole(Long userId, String roleName) {
        Long roleId;
        try {
            roleId = entityManager.createNativeQuery(
                            "select role_id from app.roles where role_name = :roleName")
                    .setParameter("roleName", roleName)
                    .getSingleResult() instanceof Number value
                    ? value.longValue()
                    : null;
        } catch (NoResultException exception) {
            throw new IllegalArgumentException("Role is not configured: " + roleName, exception);
        }

        entityManager.createNativeQuery(
                        "insert into app.user_roles (user_id, role_id) values (:userId, :roleId)")
                .setParameter("userId", userId)
                .setParameter("roleId", roleId)
                .executeUpdate();
    }

    private long countBy(String field, String value) {
        String query = "select count(user) from SignupUser user where user." + field + " = :value";
        return entityManager.createQuery(query, Long.class)
                .setParameter("value", value)
                .getSingleResult();
    }
}
