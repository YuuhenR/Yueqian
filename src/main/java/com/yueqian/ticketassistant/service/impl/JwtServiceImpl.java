package com.yueqian.ticketassistant.service.impl;

import com.yueqian.ticketassistant.config.SecurityProperties;
import com.yueqian.ticketassistant.dto.UserPrincipal;
import com.yueqian.ticketassistant.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtServiceImpl implements JwtService {

    private final SecurityProperties properties;

    public JwtServiceImpl(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public String issue(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getJwtExpireMinutes() * 60)))
                .signWith(key())
                .compact();
    }

    @Override
    public UserPrincipal verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(claims.getSubject(), claims.get("role", String.class));
    }

    private SecretKey key() {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }
}
