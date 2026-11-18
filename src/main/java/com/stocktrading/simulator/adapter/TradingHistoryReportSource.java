package com.stocktrading.simulator.adapter;

import com.stocktrading.simulator.model.Trade;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.TradeRepository;
import com.stocktrading.simulator.service.ReportService;
import com.stocktrading.simulator.service.ReportSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link ReportSource} backed by real executed trades from {@link TradeRepository}.
 * Strictly scoped to the authenticated user's own trade history.
 */
@Component
public class TradingHistoryReportSource implements ReportSource {

    private static final int PAGE_SIZE = 500;
    private static final int MAX_ROWS = 10_000;

    private final TradeRepository tradeRepository;

    public TradingHistoryReportSource(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Override
    public String slug() {
        return "trading-history";
    }

    @Override
    public ReportService.Report build(User user) {
        List<List<String>> rows = new ArrayList<>();
        for (int page = 0; rows.size() < MAX_ROWS; page++) {
            Page<Trade> batch = tradeRepository.findByUserWithStockOrderByExecutedAtDesc(
                user, PageRequest.of(page, PAGE_SIZE)
            );
            for (Trade t : batch.getContent()) {
                rows.add(List.of(
                    ReportService.STAMP.format(t.getExecutedAt()),
                    t.getStock().getSymbol(),
                    t.getSide().name(),
                    String.valueOf(t.getQuantity()),
                    t.getExecutionPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    t.getTotalAmount().setScale(2, RoundingMode.HALF_UP).toPlainString()
                ));
                if (rows.size() >= MAX_ROWS) {
                    break;
                }
            }
            if (!batch.hasNext()) {
                break;
            }
        }
        return new ReportService.Report(
            slug(),
            "Trading history",
            ReportService.subtitleFor(user),
            List.of("Date (UTC)", "Symbol", "Side", "Quantity", "Price", "Total"),
            rows
        );
    }
}
