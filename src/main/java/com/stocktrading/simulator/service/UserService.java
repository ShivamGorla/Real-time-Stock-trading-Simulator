package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.RegisterRequest;
import com.stocktrading.simulator.dto.UserResponse;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;

@Service
public class UserService implements UserDetailsService {

    private static final int MAX_SAVE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final PublicUserIdGenerator publicUserIdGenerator;
    private final EmailAuthService emailAuthService;
    private final TransactionTemplate transactionTemplate;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       WalletService walletService,
                       PublicUserIdGenerator publicUserIdGenerator,
                       EmailAuthService emailAuthService,
                       PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
        this.publicUserIdGenerator = publicUserIdGenerator;
        this.emailAuthService = emailAuthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private record RegistrationResult(User user, BigDecimal balance) {}

    /**
     * Atomically creates the user (with a unique server-generated 6-digit public ID), persistent virtual
     * wallet, and initial balance ledger entry inside a single transaction per retry attempt.
     * Retries only on a rare {@code public_user_id} unique-constraint collision; any wallet creation
     * failure rolls back the entire registration atomically.
     */
    public UserResponse registerUser(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            boolean[] userPersisted = {false};
            try {
                RegistrationResult result = transactionTemplate.execute(status -> {
                    User user = new User(
                        request.getName(),
                        request.getUsername(),
                        request.getEmail(),
                        passwordHash
                    );
                    user.setPublicUserId(publicUserIdGenerator.next());
                    user.setEmailVerified(false);

                    User savedUser = userRepository.saveAndFlush(user);
                    userPersisted[0] = true;

                    Wallet wallet = walletService.createWalletWithInitialBalance(savedUser);
                    return new RegistrationResult(savedUser, wallet.getBalance());
                });

                if (result != null) {
                    emailAuthService.sendVerificationEmail(result.user());
                    return new UserResponse(result.user(), result.balance());
                }
            } catch (DataIntegrityViolationException e) {
                if (userPersisted[0]) {
                    // Wallet/ledger creation failed after user flush — transaction rolled back; propagate failure.
                    throw e;
                }
                if (userRepository.existsByUsername(request.getUsername())) {
                    throw new IllegalArgumentException("Username is already taken");
                }
                if (userRepository.existsByEmail(request.getEmail())) {
                    throw new IllegalArgumentException("Email is already registered");
                }
                // Otherwise public_user_id collided concurrently; retry with a fresh candidate.
            }
        }
        throw new IllegalStateException("Could not create the account, please try again");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPasswordHash(),
            Collections.emptyList()
        );
    }
}
