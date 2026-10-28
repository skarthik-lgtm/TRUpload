package com.trupload.dao.auth.signup;

import com.trupload.model.auth.signup.SignupUser;

public interface SignupDao {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    SignupUser save(SignupUser user);

    void assignRole(Long userId, String roleName);

    long createAccountRequest(Long userId);

    void addRequestedWarehouse(long accountRequestId, long warehouseId);

    boolean warehouseExists(long warehouseId);
}
