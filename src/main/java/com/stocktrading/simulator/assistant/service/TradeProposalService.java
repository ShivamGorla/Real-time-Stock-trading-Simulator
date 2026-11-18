package com.stocktrading.simulator.assistant.service;

import com.stocktrading.simulator.assistant.service.dto.TradeProposalResponse;
import com.stocktrading.simulator.dto.HoldingResponse;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.dto.StockResponse;
import com.stocktrading.simulator.dto.WalletResponse;
import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.OrderSide;
import com.stocktrading.simulator.service.PortfolioService;
import com.stocktrading.simulator.service.StockService;
import com.stocktrading.simulator.service.WalletService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service calculating advisory, non-executable trade proposal previews against canonical StockSim services.
 *
 * <p><strong>Safety Invariants:</strong></p>
 * <ul>
 *   <li>Strictly {@code @Transactional(readOnly = true)}; does NOT inject {@code OrderService},
 *       {@code OrderExecutionService}, or {@code TradeService}.</li>
 *   <li>No orders, trades, wallet transactions, or holding mutations are ever created or persisted.</li>
 *   <li>No cash or shares are reserved.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TradeProposalService {

    public static final int MAX_PROPOSAL_QUANTITY = 100_000;
    public static final BigDecimal MAX_TOTAL_AMOUNT = new BigDecimal("99999999999999.99");
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Z0-9._-]{1,20}$");

    public static final String ADVISORY_NOTICE =
            "ADVISORY PREVIEW ONLY: This is a non-binding simulation preview. No order has been placed, " +
            "no funds or shares have been reserved, and no ledger records have been created. " +
            "Real-time balance and execution price will be re-validated at actual trade execution time.";

    private final StockService stockService;
    private final WalletService walletService;
    private final PortfolioService portfolioService;

    public TradeProposalService(StockService stockService,
                                WalletService walletService,
                                PortfolioService portfolioService) {
        this.stockService = stockService;
        this.walletService = walletService;
        this.portfolioService = portfolioService;
    }

    public TradeProposalResponse generateProposal(String username, String rawSymbol, OrderSide side, Integer quantity) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (side == null) {
            throw new IllegalArgumentException("Order side (BUY or SELL) is required");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be a positive integer");
        }
        if (quantity > MAX_PROPOSAL_QUANTITY) {
            throw new IllegalArgumentException("Quantity exceeds maximum allowable proposal limit: " + MAX_PROPOSAL_QUANTITY);
        }

        String normalizedSymbol = normalizeAndValidateSymbol(rawSymbol);
        StockResponse stock = stockService.getStockBySymbol(normalizedSymbol);

        BigDecimal estimatedPrice = stock.getCurrentPrice();
        if (estimatedPrice == null || estimatedPrice.compareTo(Holding.MIN_STOCK_PRICE) < 0
                || estimatedPrice.compareTo(Holding.MAX_STOCK_PRICE) > 0) {
            throw new IllegalArgumentException("Stock price is outside permitted simulation bounds");
        }
        estimatedPrice = estimatedPrice.setScale(2, RoundingMode.HALF_UP);

        BigDecimal estimatedTotalAmount = estimatedPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
        if (estimatedTotalAmount.compareTo(MAX_TOTAL_AMOUNT) > 0) {
            throw new IllegalArgumentException("Estimated total order amount exceeds allowed monetary limit");
        }

        WalletResponse wallet = walletService.getWalletResponseByUsername(username);
        BigDecimal currentBalance = wallet.getBalance();
        String currency = wallet.getCurrency() != null ? wallet.getCurrency() : "INR";

        PortfolioResponse portfolio = portfolioService.getPortfolio(username, false);
        int currentHoldingQuantity = 0;
        if (portfolio.getHoldings() != null) {
            Optional<HoldingResponse> existingHolding = portfolio.getHoldings().stream()
                    .filter(h -> normalizedSymbol.equalsIgnoreCase(h.getSymbol()))
                    .findFirst();
            if (existingHolding.isPresent() && existingHolding.get().getQuantity() != null) {
                currentHoldingQuantity = existingHolding.get().getQuantity();
            }
        }

        boolean isExecutable;
        BigDecimal estimatedBalanceAfter = null;
        String validationMessage;

        if (side == OrderSide.BUY) {
            if (currentBalance.compareTo(estimatedTotalAmount) >= 0) {
                isExecutable = true;
                estimatedBalanceAfter = currentBalance.subtract(estimatedTotalAmount);
                validationMessage = "Sufficient virtual funds available. Estimated purchase can proceed upon user confirmation.";
            } else {
                isExecutable = false;
                validationMessage = String.format(
                        "Insufficient funds. Required: %s %s, Available: %s %s",
                        currency, estimatedTotalAmount, currency, currentBalance
                );
            }
        } else {
            if (currentHoldingQuantity >= quantity) {
                isExecutable = true;
                estimatedBalanceAfter = currentBalance.add(estimatedTotalAmount);
                validationMessage = "Sufficient shares held. Estimated sale can proceed upon user confirmation.";
            } else {
                isExecutable = false;
                validationMessage = String.format(
                        "Insufficient shares held. Requested to sell: %d %s, but user currently holds: %d shares.",
                        quantity, normalizedSymbol, currentHoldingQuantity
                );
            }
        }

        return new TradeProposalResponse(
                stock.getSymbol(),
                stock.getCompanyName(),
                side,
                quantity,
                estimatedPrice,
                estimatedTotalAmount,
                currency,
                currentBalance,
                currentHoldingQuantity,
                isExecutable,
                estimatedBalanceAfter,
                validationMessage,
                ADVISORY_NOTICE,
                Instant.now()
        );
    }

    public static String normalizeAndValidateSymbol(String rawSymbol) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            throw new IllegalArgumentException("Stock symbol cannot be empty");
        }
        String normalized = rawSymbol.trim().toUpperCase();
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid stock symbol format: '" + rawSymbol + "'");
        }
        return normalized;
    }
}
