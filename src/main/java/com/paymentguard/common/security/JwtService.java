package com.paymentguard.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Key key;
    private final long exp;

    public JwtService(@Value("${security.jwt.secret}") String secret, @Value("${security.jwt.expiration-minutes}") long exp) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.exp = exp;
    }

    public String generate(String email, String role) {
        Date now = Date.from(Instant.now());
        return Jwts.builder().subject(email).claim("role", role).issuedAt(now).expiration(new Date(now.getTime() + exp * 60000)).signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build().parseSignedClaims(token).getPayload();
    }
}
