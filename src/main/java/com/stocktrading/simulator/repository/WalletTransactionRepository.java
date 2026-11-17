package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.TransactionCategory;
import com.stocktrading.simulator.model.TransactionType;
import com.stocktrading.simulator.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByWalletIdAndReferenceId(Long walletId, String referenceId);

    boolean existsByWalletIdAndReferenceId(Long walletId, String referenceId);

    Page<WalletTransaction> findByWalletId(Long walletId, Pageable pageable);

    @Query("SELECT wt FROM WalletTransaction wt WHERE wt.wallet.id = :walletId " +
           "AND (:type IS NULL OR wt.type = :type) " +
           "AND (:category IS NULL OR wt.category = :category) " +
           "AND (:fromInstant IS NULL OR wt.createdAt >= :fromInstant) " +
           "AND (:toInstant IS NULL OR wt.createdAt < :toInstant)")
    Page<WalletTransaction> findFilteredTransactions(
        @Param("walletId") Long walletId,
        @Param("type") TransactionType type,
        @Param("category") TransactionCategory category,
        @Param("fromInstant") Instant fromInstant,
        @Param("toInstant") Instant toInstant,
        Pageable pageable
    );
}
