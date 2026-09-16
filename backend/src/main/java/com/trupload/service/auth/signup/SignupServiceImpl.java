package com.trupload.service.auth.signup;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

import jakarta.transaction.Transactional;

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
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String username = request.username().trim();

        validateEmail(email);
        if (signupDao.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (signupDao.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered");
        }

        SignupUser user = signupDao.save(new SignupUser(
                username,
                email,
                passwordEncoder.encode(request.password()),
                request.role().trim(),
                LocalDateTime.now()));

        return new SignupResponse(
                "Account created successfully",
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedDate());
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidEmailException("Enter a valid email address");
        }
    }
}
