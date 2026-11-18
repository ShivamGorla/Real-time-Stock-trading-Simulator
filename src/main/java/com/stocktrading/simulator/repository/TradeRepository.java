package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.Trade;
import com.stocktrading.simulator.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    Page<Trade> findByUser(User user, Pageable pageable);

    Page<Trade> findByUserOrderByExecutedAtDesc(User user, Pageable pageable);

    @Query(value = "SELECT t FROM Trade t JOIN FETCH t.stock WHERE t.user = :user ORDER BY t.executedAt DESC, t.id DESC",
           countQuery = "SELECT count(t) FROM Trade t WHERE t.user = :user")
    Page<Trade> findByUserWithStockOrderByExecutedAtDesc(@Param("user") User user, Pageable pageable);

    Optional<Trade> findByIdAndUser(Long id, User user);

    boolean existsByOrder(Order order);
}
