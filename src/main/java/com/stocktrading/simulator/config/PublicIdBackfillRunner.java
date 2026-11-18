package com.stocktrading.simulator.config;

import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.service.PublicUserIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Collision-safe startup runner that:
 * <ol>
 *   <li>Backfills unique 6-digit {@code public_user_id} values for any legacy users where it is NULL,
 *       executing each user inside an isolated transaction with bounded collision retry.</li>
 *   <li>Logs a non-destructive warning if any legacy user accounts are missing a persistent wallet,
 *       without ever automatically mutating balances or granting duplicate initial funds.</li>
 * </ol>
 */
@Component
public class PublicIdBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublicIdBackfillRunner.class);
    private static final int MAX_COLLISION_RETRIES = 5;

    private final UserRepository userRepository;
    private final PublicUserIdGenerator generator;
    private final TransactionTemplate transactionTemplate;

    public PublicIdBackfillRunner(UserRepository userRepository,
                                  PublicUserIdGenerator generator,
                                  PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.generator = generator;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        int filled = 0;
        for (User user : userRepository.findByPublicUserIdIsNull()) {
            if (assignPublicIdWithRetry(user.getId())) {
                filled++;
            }
        }
        if (filled > 0) {
            log.info("Backfilled public user IDs for {} existing users", filled);
        }

        List<Long> missingWalletUserIds = userRepository.findUserIdsWithoutWallet();
        if (!missingWalletUserIds.isEmpty()) {
            log.warn(
                "Detected {} legacy user account(s) without a persistent wallet (userIds={}). "
                    + "Run the backed-up legacy wallet migration before enabling trading for those accounts.",
                missingWalletUserIds.size(),
                missingWalletUserIds
            );
        }
    }

    boolean assignPublicIdWithRetry(Long userId) {
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            try {
                Boolean assigned = transactionTemplate.execute(status -> {
                    User managed = userRepository.findById(userId).orElse(null);
                    if (managed == null || managed.getPublicUserId() != null) {
                        // Already deleted or backfilled concurrently by another node — idempotent no-op
                        return false;
                    }
                    managed.setPublicUserId(generator.next());
                    userRepository.saveAndFlush(managed);
                    return true;
                });
                return Boolean.TRUE.equals(assigned);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Public user ID collision on backfill attempt {} for userId={}; retrying", attempt + 1, userId);
            }
        }
        throw new IllegalStateException("Could not backfill unique public_user_id after "
            + MAX_COLLISION_RETRIES + " attempts for userId=" + userId);
    }
}
