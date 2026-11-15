package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findBySymbolIgnoreCase(String symbol);

    boolean existsBySymbolIgnoreCase(String symbol);

    List<Stock> findBySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCaseOrderBySymbolAsc(String symbol, String companyName);

    List<Stock> findAllByOrderBySymbolAsc();
}
