package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.PaymentTransaction;
import com.stocktrading.simulator.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByUserAndIdempotencyKey(User user, String idempotencyKey);

    Optional<PaymentTransaction> findByIdAndUser(Long id, User user);

    Page<PaymentTransaction> findByUserOrderByCreatedAtDescIdDesc(User user, Pageable pageable);

    /** Null when the user has no payment in that status. */
    @Query("select sum(p.amount) from PaymentTransaction p where p.user = :user and p.status = :status")
    BigDecimal sumAmount(@Param("user") User user, @Param("status") PaymentTransaction.Status status);

    /** Conditional state change: returns 1 for exactly one caller, which is what makes crediting happen once. */
    @Modifying
    @Query("update PaymentTransaction p set p.status = :to, p.completedAt = :now, p.failureReason = :reason "
        + "where p.id = :id and p.status = :from")
    int transition(@Param("id") Long id,
                   @Param("from") PaymentTransaction.Status from,
                   @Param("to") PaymentTransaction.Status to,
                   @Param("now") Instant now,
                   @Param("reason") String reason);
}
