package com.trupload.dao.auth.signup;

import com.trupload.model.auth.signup.SignupUser;

public interface SignupDao {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    SignupUser save(SignupUser user);

    SignupUser save(SignupUser user, String status);

    void assignRole(Long userId, String roleName);

    long createAccountRequest(Long userId);

    void addRequestedWarehouse(long accountRequestId, long warehouseId);

    boolean warehouseExists(long warehouseId);

    long findUserIdByEmail(String email);

    void assignWarehouse(Long userId, long warehouseId, long assignedBy);

    void recordAdminCreatedUser(long actorId, long userId, int warehouseCount);
}
