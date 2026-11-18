package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.ChangePasswordRequest;
import com.stocktrading.simulator.dto.UpdateProfileRequest;
import com.stocktrading.simulator.dto.UserResponse;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Profile changes for the already-authenticated user. The caller passes the username from the security context. */
@Service
public class ProfileService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    public ProfileService(UserRepository users, PasswordEncoder passwordEncoder, WalletService walletService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
    }

    @Transactional
    public UserResponse updateName(String username, UpdateProfileRequest request) {
        User user = load(username);
        user.setName(request.name().trim());
        BigDecimal balance = walletService.findWalletByUserId(user.getId())
            .map(Wallet::getBalance)
            .orElse(BigDecimal.ZERO);
        return new UserResponse(user, balance);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = load(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (request.newPassword().equals(request.currentPassword())) {
            throw new IllegalArgumentException("New password must be different from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User load(String username) {
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
