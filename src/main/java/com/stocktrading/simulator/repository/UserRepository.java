package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPublicUserId(Integer publicUserId);

    List<User> findByPublicUserIdIsNull();

    @Query("SELECT u.id FROM User u WHERE NOT EXISTS (SELECT 1 FROM Wallet w WHERE w.user = u)")
    List<Long> findUserIdsWithoutWallet();
}
