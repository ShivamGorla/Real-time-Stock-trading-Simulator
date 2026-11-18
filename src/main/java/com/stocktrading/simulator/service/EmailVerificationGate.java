package com.stocktrading.simulator.service;

import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Decides, after the password has already been checked, whether a login may proceed. Enforcement is
 * off unless app.auth.email-verification-required=true (env EMAIL_VERIFICATION_REQUIRED). Legacy
 * accounts (emailVerified == null) are always allowed.
 */
@Component
public class EmailVerificationGate {

    private final UserRepository users;
    private final boolean required;

    public EmailVerificationGate(UserRepository users,
                                 @Value("${app.auth.email-verification-required:false}") boolean required) {
        this.users = users;
        this.required = required;
    }

    public boolean isLoginAllowed(String username) {
        return !required || users.findByUsername(username).map(u -> u.isEmailVerifiedOrLegacy()).orElse(false);
    }
}
