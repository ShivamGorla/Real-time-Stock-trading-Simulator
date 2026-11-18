package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.AuthToken;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.AuthTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** Creates and consumes single-use verification / reset tokens. Raw tokens are never stored or logged. */
@Service
public class AuthTokenService {

    private final AuthTokenRepository tokens;
    private final SecureRandom random = new SecureRandom();
    private final Duration verifyTtl;
    private final Duration resetTtl;
    private final Duration resendCooldown;

    public AuthTokenService(AuthTokenRepository tokens,
                            @Value("${app.auth.verify-token-hours:24}") long verifyHours,
                            @Value("${app.auth.reset-token-minutes:30}") long resetMinutes,
                            @Value("${app.auth.resend-cooldown-seconds:60}") long cooldownSeconds) {
        this.tokens = tokens;
        this.verifyTtl = Duration.ofHours(verifyHours);
        this.resetTtl = Duration.ofMinutes(resetMinutes);
        this.resendCooldown = Duration.ofSeconds(cooldownSeconds);
    }

    /** True when the last token of this type was issued too recently to issue another. */
    @Transactional(readOnly = true)
    public boolean isCoolingDown(User user, AuthToken.Type type) {
        return tokens.findFirstByUserAndTypeOrderByCreatedAtDesc(user, type)
            .map(t -> t.getCreatedAt().plus(resendCooldown).isAfter(Instant.now()))
            .orElse(false);
    }

    /** Invalidates older tokens of the type, stores a new hashed one and returns the raw token. */
    @Transactional
    public String issue(User user, AuthToken.Type type) {
        Instant now = Instant.now();
        tokens.invalidateOutstanding(user, type, now);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration ttl = type == AuthToken.Type.VERIFY_EMAIL ? verifyTtl : resetTtl;
        tokens.save(new AuthToken(user, type, sha256(raw), now, now.plus(ttl)));
        return raw;
    }

    /**
     * Validates and consumes a token, returning its owner. Throws IllegalArgumentException (HTTP 400)
     * with a distinct message for invalid, already-used and expired tokens.
     */
    @Transactional
    public User consume(String rawToken, AuthToken.Type type) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 128) {
            throw new IllegalArgumentException("This link is invalid.");
        }
        AuthToken token = tokens.findByTokenHashAndType(sha256(rawToken.trim()), type)
            .orElseThrow(() -> new IllegalArgumentException("This link is invalid."));
        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("This link has already been used.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("This link has expired. Request a new one.");
        }
        if (tokens.markUsed(token.getId(), now) != 1) {
            throw new IllegalArgumentException("This link has already been used.");
        }
        return token.getUser();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
