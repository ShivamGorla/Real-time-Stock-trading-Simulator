package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.HoldingResponse;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.Stock;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.model.Wallet;
import com.stocktrading.simulator.repository.HoldingRepository;
import com.stocktrading.simulator.repository.UserRepository;
import com.stocktrading.simulator.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service orchestrating read-only portfolio position aggregation and valuation.
 * Aggregates cash balance, active stock holdings, current market valuations,
 * and lifetime cumulative realized profit/loss.
 */
@Service
public class PortfolioService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(UserRepository userRepository,
                            WalletRepository walletRepository,
                            HoldingRepository holdingRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.holdingRepository = holdingRepository;
    }

    /**
     * Retrieves the portfolio valuation and position list for the authenticated user.
     *
     * @param username      the authenticated username
     * @param includeClosed whether to include closed positions (quantity == 0) in the holdings list
     * @return the unified PortfolioResponse
     */
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(String username, boolean includeClosed) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        Long userId = user.getId();

        Wallet wallet = walletRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalStateException("Wallet not found for user: " + username));

        List<Holding> allHoldings = holdingRepository.findByUserIdWithStock(userId);

        BigDecimal totalInvestedValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCurrentMarketValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRealizedPnl = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<HoldingResponse> holdingResponses = new ArrayList<>();

        for (Holding holding : allHoldings) {
            // Lifetime realized P&L is summed across ALL holdings (both open and closed)
            if (holding.getTotalRealizedPnl() != null) {
                totalRealizedPnl = totalRealizedPnl.add(holding.getTotalRealizedPnl());
            }

            int quantity = (holding.getQuantity() != null) ? holding.getQuantity() : 0;
            Stock stock = holding.getStock();
            BigDecimal price = stock.getCurrentPrice();
            BigDecimal avgCost = holding.getAverageCost();

            BigDecimal investedValue;
            BigDecimal currentMarketValue;
            BigDecimal unrealizedPnl;
            BigDecimal unrealizedReturnPercentage;

            if (quantity > 0) {
                investedValue = BigDecimal.valueOf(quantity).multiply(avgCost).setScale(2, RoundingMode.HALF_UP);
                currentMarketValue = BigDecimal.valueOf(quantity).multiply(price).setScale(2, RoundingMode.HALF_UP);
                unrealizedPnl = currentMarketValue.subtract(investedValue);

                if (investedValue.compareTo(BigDecimal.ZERO) == 0) {
                    unrealizedReturnPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                } else {
                    unrealizedReturnPercentage = unrealizedPnl.multiply(HUNDRED)
                        .divide(investedValue, 2, RoundingMode.HALF_UP);
                }

                totalInvestedValue = totalInvestedValue.add(investedValue);
                totalCurrentMarketValue = totalCurrentMarketValue.add(currentMarketValue);
            } else {
                investedValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                currentMarketValue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                unrealizedPnl = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                unrealizedReturnPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            // Filter holdings list by active vs closed
            if (quantity > 0 || includeClosed) {
                HoldingResponse response = new HoldingResponse(
                    stock.getId(),
                    stock.getSymbol(),
                    stock.getCompanyName(),
                    quantity,
                    avgCost,
                    price,
                    investedValue,
                    currentMarketValue,
                    unrealizedPnl,
                    unrealizedReturnPercentage,
                    holding.getTotalRealizedPnl(),
                    holding.getUpdatedAt()
                );
                holdingResponses.add(response);
            }
        }

        BigDecimal totalUnrealizedPnl = totalCurrentMarketValue.subtract(totalInvestedValue);
        BigDecimal totalPnl = totalRealizedPnl.add(totalUnrealizedPnl);

        BigDecimal unrealizedReturnPercentage;
        if (totalInvestedValue.compareTo(BigDecimal.ZERO) == 0) {
            unrealizedReturnPercentage = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            unrealizedReturnPercentage = totalUnrealizedPnl.multiply(HUNDRED)
                .divide(totalInvestedValue, 2, RoundingMode.HALF_UP);
        }

        BigDecimal totalAccountEquity = wallet.getBalance().add(totalCurrentMarketValue);
        String currency = (wallet.getCurrency() != null && !wallet.getCurrency().isBlank())
            ? wallet.getCurrency()
            : "INR";

        return new PortfolioResponse(
            userId,
            user.getUsername(),
            wallet.getBalance(),
            currency,
            totalInvestedValue,
            totalCurrentMarketValue,
            totalUnrealizedPnl,
            totalRealizedPnl,
            totalPnl,
            unrealizedReturnPercentage,
            totalAccountEquity,
            Instant.now(),
            holdingResponses
        );
    }
}
