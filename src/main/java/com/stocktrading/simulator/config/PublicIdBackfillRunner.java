package com.stocktrading.simulator.config;

import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.service.PublicUserIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Gives every pre-existing user (public_user_id IS NULL) a public ID at startup. Only that column is
 * written. Idempotent: once nobody is NULL it does nothing.
 */
@Component
public class PublicIdBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);

    private final UserRepository userRepository;
    private final PublicUserIdGenerator generator;

    public PublicIdBackfillRunner(UserRepository userRepository, PublicUserIdGenerator generator) {
        this.userRepository = userRepository;
        this.generator = generator;
    }

    @Override
    public void run(ApplicationArguments args) {
        int filled = 0;
        for (User user : userRepository.findByPublicUserIdIsNull()) {
            user.setPublicUserId(generator.next());
            userRepository.saveAndFlush(user);
            filled++;
        }
        if (filled > 0) {
            log.info("Backfilled public user IDs for {} existing users", filled);
        }
    }
}
