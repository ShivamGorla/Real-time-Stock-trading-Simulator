package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.PaymentResponse;
import com.stocktrading.simulator.exception.SettlementUnavailableException;
import com.stocktrading.simulator.model.PaymentTransaction;
import com.stocktrading.simulator.model.PaymentTransaction.Status;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.port.WalletTopUpPort;
import com.stocktrading.simulator.repository.PaymentTransactionRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Simulated virtual-money top-up. The client sends only an amount and an Idempotency-Key; the
 * outcome is decided server-side. Wallet crediting happens through {@link WalletTopUpPort} in the
 * exact same database transaction as the {@code PENDING -> SUCCESS} conditional UPDATE, so a payment
 * can credit at most once and rolls back cleanly on any wallet mutation failure.
 *
 * <p>Fails closed with HTTP 503 ({@link SettlementUnavailableException}) if no {@link WalletTopUpPort}
 * bean is configured — never falls back to a ledger-only mode.</p>
 */
@Service
public class PaymentService {

    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final UserRepository users;
    private final PaymentTransactionRepository payments;
    private final ObjectProvider<WalletTopUpPort> wallet;
    private final TransactionTemplate tx;
    private final BigDecimal min;
    private final BigDecimal max;
    private final BigDecimal rejectAmount;

    public PaymentService(UserRepository users, PaymentTransactionRepository payments,
                          ObjectProvider<WalletTopUpPort> wallet, PlatformTransactionManager txManager,
                          @Value("${app.payments.min-amount:100}") BigDecimal min,
                          @Value("${app.payments.max-amount:500000}") BigDecimal max,
                          @Value("${app.payments.simulated-reject-amount:666.66}") BigDecimal rejectAmount) {
        this.users = users;
        this.payments = payments;
        this.wallet = wallet;
        this.tx = new TransactionTemplate(txManager);
        this.min = min;
        this.max = max;
        this.rejectAmount = rejectAmount;
    }

    public PaymentResponse topUp(String username, String idempotencyKey, BigDecimal amount) {
        if (idempotencyKey == null || !KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key header is required (8-64 letters, digits, - or _)");
        }
        BigDecimal value = validAmount(amount);

        // Fail closed if no real WalletTopUpPort adapter is present
        WalletTopUpPort port = wallet.getIfAvailable();
        if (port == null) {
            throw new SettlementUnavailableException("Wallet top-up settlement adapter is unavailable");
        }

        User user = load(username);
        PaymentTransaction payment = findOrCreate(user, idempotencyKey, value);
        if (payment.getStatus() == Status.PENDING) {
            payment = settle(payment.getId(), port);
        }
        return PaymentResponse.from(payment);
    }

    public PaymentResponse get(String username, Long id) {
        return payments.findByIdAndUser(id, load(username))
            .map(PaymentResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public List<PaymentResponse> history(String username, int page, int size) {
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return payments.findByUserOrderByCreatedAtDescIdDesc(load(username), pageRequest)
            .map(PaymentResponse::from).getContent();
    }

    private BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount can have at most 2 decimal places");
        }
        BigDecimal value = amount.setScale(2);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("Amount must be between " + min.toPlainString() + " and " + max.toPlainString());
        }
        return value;
    }

    private PaymentTransaction findOrCreate(User user, String key, BigDecimal amount) {
        PaymentTransaction existing = payments.findByUserAndIdempotencyKey(user, key).orElse(null);
        if (existing == null) {
            try {
                return payments.saveAndFlush(new PaymentTransaction(user, key, amount));
            } catch (DataIntegrityViolationException e) {
                // Same key inserted concurrently: use the winner's row.
                existing = payments.findByUserAndIdempotencyKey(user, key).orElseThrow(() -> e);
            }
        }
        if (existing.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("This Idempotency-Key was already used with a different amount");
        }
        return existing;
    }

    /** Settles a PENDING payment atomically; safe to call repeatedly or concurrently. */
    private PaymentTransaction settle(Long id, WalletTopUpPort port) {
        tx.executeWithoutResult(status -> {
            PaymentTransaction p = payments.findById(id).orElseThrow();
            Instant now = Instant.now();
            if (approves(p.getAmount())) {
                // Conditional PENDING -> SUCCESS transition and wallet credit commit or roll back together
                if (payments.transition(id, Status.PENDING, Status.SUCCESS, now, null) == 1) {
                    port.creditTopUp(p.getUser().getId(), p.getAmount(), "TOPUP-" + id);
                }
            } else {
                payments.transition(id, Status.PENDING, Status.FAILED, now, "Declined by the simulated payment processor");
            }
        });
        return payments.findById(id).orElseThrow();
    }

    /** Simulated processor: approves everything except the configured demo amount. */
    private boolean approves(BigDecimal amount) {
        return amount.compareTo(rejectAmount) != 0;
    }

    private User load(String username) {
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
