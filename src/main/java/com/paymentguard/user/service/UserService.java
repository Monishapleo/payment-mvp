package com.paymentguard.user.service;

import com.paymentguard.user.dto.*;
import com.paymentguard.user.entity.*;
import com.paymentguard.user.repository.UserRepository;
import com.paymentguard.common.exception.ApiException;
import com.paymentguard.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserService(UserRepository r, PasswordEncoder e, JwtService j) {
        repo = r;
        encoder = e;
        jwt = j;
    }

    @Transactional
    public void register(RegisterRequest req) {
        if (repo.existsByEmail(req.email().toLowerCase())) throw new ApiException(409, "Email already registered");
        User u = new User();
        u.setName(req.name());
        u.setEmail(req.email().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(Role.CUSTOMER);
        repo.save(u);
    }

    public AuthResponse login(LoginRequest req) {
        User u = repo.findByEmail(req.email().toLowerCase()).orElseThrow(() -> new ApiException(401, "Invalid credentials"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) throw new ApiException(401, "Invalid credentials");
        return new AuthResponse(jwt.generate(u.getEmail(), u.getRole().name()), u.getEmail(), u.getRole().name());
    }
}
