package com.stocktrading.simulator.repository;

import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByUser(User user);

    Optional<Wallet> findByUserUsername(String username);

    boolean existsByUserId(Long userId);
}
