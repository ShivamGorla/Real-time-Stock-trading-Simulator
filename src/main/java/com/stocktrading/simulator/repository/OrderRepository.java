package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    Optional<Order> findByIdAndUser(Long id, User user);

    boolean existsByUserAndClientOrderId(User user, String clientOrderId);

    Optional<Order> findByUserAndClientOrderId(User user, String clientOrderId);
}
