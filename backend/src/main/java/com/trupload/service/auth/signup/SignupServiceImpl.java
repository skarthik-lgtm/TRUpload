package com.trupload.service.auth.signup;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.trupload.dao.auth.signup.SignupDao;
import com.trupload.exception.auth.signup.InvalidEmailException;
import com.trupload.model.auth.signup.SignupRequest;
import com.trupload.model.auth.signup.SignupResponse;
import com.trupload.model.auth.signup.SignupUser;

@Service
public class SignupServiceImpl implements SignupService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final SignupDao signupDao;
    private final PasswordEncoder passwordEncoder;

    public SignupServiceImpl(SignupDao signupDao, PasswordEncoder passwordEncoder) {
        this.signupDao = signupDao;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(transactionManager = "sqlServerTransactionManager")
    public SignupResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();

        validateEmail(email);
        if (signupDao.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (signupDao.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered");
        }

        List<Long> warehouseIds = request.warehouseIds() == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.warehouseIds()));
        if (warehouseIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one warehouse.");
        }
        for (Long warehouseId : warehouseIds) {
            if (warehouseId == null || !signupDao.warehouseExists(warehouseId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A selected warehouse is invalid.");
            }
        }

        SignupUser user = signupDao.save(new SignupUser(
                username,
                email,
                firstName,
                lastName,
                passwordEncoder.encode(request.password()),
                LocalDateTime.now()));
        signupDao.assignRole(user.getId(), "USER");
        long accountRequestId = signupDao.createAccountRequest(user.getId());
        for (Long warehouseId : warehouseIds) {
            signupDao.addRequestedWarehouse(accountRequestId, warehouseId);
        }

        return new SignupResponse(
                "Registration submitted for administrator review",
                user.getUsername(),
                user.getEmail(),
                "USER",
                user.getCreatedDate(),
                accountRequestId,
                "PENDING");
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException("Enter a valid email address");
        }
    }
}
