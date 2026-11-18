package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Holding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByUserIdAndStockId(Long userId, Long stockId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM Holding h WHERE h.user.id = :userId AND h.stock.id = :stockId")
    Optional<Holding> findByUserIdAndStockIdForUpdate(@Param("userId") Long userId, @Param("stockId") Long stockId);

    List<Holding> findByUserId(Long userId);

    List<Holding> findByUserIdAndQuantityGreaterThan(Long userId, Integer minQuantity);

    @Query("SELECT h FROM Holding h JOIN FETCH h.stock WHERE h.user.id = :userId ORDER BY h.stock.symbol ASC")
    List<Holding> findByUserIdWithStock(@Param("userId") Long userId);
}
