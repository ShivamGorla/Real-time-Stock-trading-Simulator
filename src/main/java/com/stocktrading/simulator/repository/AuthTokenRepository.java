package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.AuthToken;
import com.stocktrading.simulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {

    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthToken.Type type);

    Optional<AuthToken> findFirstByUserAndTypeOrderByCreatedAtDesc(User user, AuthToken.Type type);

    /** Atomic single-use: returns 1 only for the one caller that actually consumes the token. */
    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** Invalidates every unused token of that type for the user (used when a new one is issued). */
    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.user = :user and t.type = :type and t.usedAt is null")
    int invalidateOutstanding(@Param("user") User user, @Param("type") AuthToken.Type type, @Param("now") Instant now);
}
