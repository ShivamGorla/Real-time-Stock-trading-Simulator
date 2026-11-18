package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.StockPriceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StockPriceHistoryRepository extends JpaRepository<StockPriceHistory, Long> {

    @Query("SELECT h FROM StockPriceHistory h WHERE h.stock = :stock ORDER BY h.recordedAt DESC, h.id DESC")
    List<StockPriceHistory> findRecentHistoryByStock(@Param("stock") Stock stock, Pageable pageable);

    long countByStock(Stock stock);
}
