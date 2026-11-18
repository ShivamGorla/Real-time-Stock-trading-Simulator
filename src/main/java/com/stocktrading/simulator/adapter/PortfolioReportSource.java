package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.model.Holding;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.HoldingRepository;
import com.stocktrading.simulator.service.ReportService;
import com.stocktrading.simulator.service.ReportSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ReportSource} backed by real persistent holdings and live mark-to-market stock prices
 * from {@link HoldingRepository}. Strictly scoped to the authenticated user's own positions.
 */
@Component
public class PortfolioReportSource implements ReportSource {

    private final HoldingRepository holdingRepository;

    public PortfolioReportSource(HoldingRepository holdingRepository) {
        this.holdingRepository = holdingRepository;
    }

    @Override
    public String slug() {
        return "portfolio";
    }

    @Override
    public ReportService.Report build(User user) {
        List<Holding> holdings = holdingRepository.findByUserIdWithStock(user.getId());
        List<List<String>> rows = new ArrayList<>();

        for (Holding h : holdings) {
            int qty = h.getQuantity() != null ? h.getQuantity() : 0;
            if (qty <= 0) {
                continue;
            }

            BigDecimal avgCost = h.getAverageCost();
            BigDecimal currentPrice = h.getStock().getCurrentPrice().setScale(2, RoundingMode.HALF_UP);
            BigDecimal invested = BigDecimal.valueOf(qty).multiply(avgCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal marketValue = BigDecimal.valueOf(qty).multiply(currentPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPnl = marketValue.subtract(invested).setScale(2, RoundingMode.HALF_UP);

            rows.add(List.of(
                h.getStock().getSymbol(),
                String.valueOf(qty),
                avgCost.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                currentPrice.toPlainString(),
                marketValue.toPlainString(),
                unrealizedPnl.toPlainString()
            ));
        }

        return new ReportService.Report(
            slug(),
            "Portfolio summary",
            ReportService.subtitleFor(user),
            List.of("Symbol", "Quantity", "Avg cost", "Current price", "Value", "Profit / loss"),
            rows
        );
    }
}
