package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.Trade;
import com.stocktrading.simulator.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    Page<Trade> findByUserOrderByExecutedAtDesc(User user, Pageable pageable);

    Optional<Trade> findByIdAndUser(Long id, User user);

    boolean existsByOrder(Order order);
}
