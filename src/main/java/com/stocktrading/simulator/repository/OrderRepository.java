package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.Order;
import com.stocktrading.simulator.model.OrderSide;
import com.stocktrading.simulator.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserOrderByCreatedAtDesc(User user);

    @Query(
            value = "SELECT o FROM Order o JOIN FETCH o.stock WHERE o.user = :user " +
                    "AND (:side IS NULL OR o.side = :side) " +
                    "AND (:symbol IS NULL OR UPPER(o.stock.symbol) = :symbol) " +
                    "ORDER BY o.createdAt DESC, o.id DESC",
            countQuery = "SELECT COUNT(o) FROM Order o WHERE o.user = :user " +
                    "AND (:side IS NULL OR o.side = :side) " +
                    "AND (:symbol IS NULL OR UPPER(o.stock.symbol) = :symbol)"
    )
    Page<Order> findUserOrdersFiltered(@Param("user") User user,
                                       @Param("side") OrderSide side,
                                       @Param("symbol") String symbol,
                                       Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user = :user " +
            "AND (:side IS NULL OR o.side = :side) " +
            "AND (:symbol IS NULL OR UPPER(o.stock.symbol) = :symbol)")
    long countUserOrdersFiltered(@Param("user") User user,
                                 @Param("side") OrderSide side,
                                 @Param("symbol") String symbol);

    Optional<Order> findByIdAndUser(Long id, User user);

    @Query("SELECT o FROM Order o JOIN FETCH o.user JOIN FETCH o.stock WHERE o.id = :id")
    Optional<Order> findByIdWithDetails(@Param("id") Long id);

    boolean existsByUserAndClientOrderId(User user, String clientOrderId);

    Optional<Order> findByUserAndClientOrderId(User user, String clientOrderId);
}
