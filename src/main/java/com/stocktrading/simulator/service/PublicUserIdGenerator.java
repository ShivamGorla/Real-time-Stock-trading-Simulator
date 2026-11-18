package com.stocktrading.simulator.service;

import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Allocates random six-digit public user IDs (100000-999999). The existence check only avoids most
 * collisions; the UNIQUE constraint on users.public_user_id is the real guarantee and callers must
 * retry on a constraint violation.
 */
@Service
public class PublicUserIdGenerator {

    static final int MIN = 100_000;
    static final int MAX = 999_999;
    private static final int MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();

    public PublicUserIdGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ponytail: random pick + exists check over 900k IDs; upgrade to a DB sequence if user count approaches ~100k.
    public int next() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            int candidate = MIN + random.nextInt(MAX - MIN + 1);
            if (!userRepository.existsByPublicUserId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique public user ID");
    }
}
