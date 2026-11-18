package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.ResetPasswordRequest;
import com.stocktrading.simulator.model.AuthToken;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification and password recovery. resend/forgot never reveal whether an address is
 * registered: they do their work only for a matching account and return nothing either way.
 */
@Service
public class EmailAuthService {

    private final UserRepository users;
    private final AuthTokenService tokens;
    private final EmailService email;
    private final PasswordEncoder passwordEncoder;

    public EmailAuthService(UserRepository users, AuthTokenService tokens,
                            EmailService email, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.tokens = tokens;
        this.email = email;
        this.passwordEncoder = passwordEncoder;
    }

    /** Called after registration. Never throws: a mail problem must not fail sign-up. */
    public void sendVerificationEmail(User user) {
        try {
            email.sendVerification(user, tokens.issue(user, AuthToken.Type.VERIFY_EMAIL));
        } catch (RuntimeException e) {
            // account exists; user can use "resend verification"
        }
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        User user = tokens.consume(rawToken, AuthToken.Type.VERIFY_EMAIL);
        user.setEmailVerified(true);
    }

    public void resendVerification(String address) {
        users.findByEmailIgnoreCase(address.trim())
            .filter(u -> !u.isEmailVerifiedOrLegacy())
            .filter(u -> !tokens.isCoolingDown(u, AuthToken.Type.VERIFY_EMAIL))
            .ifPresent(this::sendVerificationEmail);
    }

    public void forgotPassword(String address) {
        users.findByEmailIgnoreCase(address.trim())
            .filter(u -> !tokens.isCoolingDown(u, AuthToken.Type.RESET_PASSWORD))
            .ifPresent(u -> email.sendPasswordReset(u, tokens.issue(u, AuthToken.Type.RESET_PASSWORD)));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        User user = tokens.consume(request.token(), AuthToken.Type.RESET_PASSWORD);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setEmailVerified(true); // the reset link proved control of the mailbox
    }
}
