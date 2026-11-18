package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.PaymentTransaction;
import com.stocktrading.simulator.model.User;
import com.stocktrading.simulator.repository.PaymentTransactionRepository;
import com.stocktrading.simulator.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds report data for the authenticated user only (the username comes from the security context)
 * and renders it as CSV or PDF.
 */
@Service
public class ReportService {

    public record Report(String slug, String title, String subtitle, List<String> columns, List<List<String>> rows) {}

    public record Rendered(byte[] body, String contentType, String filename) {}

    public static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final int PAGE_SIZE = 500;
    private static final int MAX_ROWS = 10_000;

    private final UserRepository users;
    private final PaymentTransactionRepository payments;
    private final List<ReportSource> sources;

    public ReportService(UserRepository users, PaymentTransactionRepository payments, List<ReportSource> sources) {
        this.users = users;
        this.payments = payments;
        this.sources = sources;
    }

    /** Trading history, from a {@link ReportSource} backed by executed trades. */
    @Transactional(readOnly = true)
    public Report tradingHistory(String username) {
        return fromSource("trading-history", "Trading history", username,
            List.of("Date (UTC)", "Symbol", "Side", "Quantity", "Price", "Total"));
    }

    /** Portfolio holdings and valuation summary, from a {@link ReportSource} backed by persistent holdings. */
    @Transactional(readOnly = true)
    public Report portfolio(String username) {
        return fromSource("portfolio", "Portfolio summary", username,
            List.of("Symbol", "Quantity", "Avg cost", "Current price", "Value", "Profit / loss"));
    }

    private Report fromSource(String slug, String title, String username, List<String> columns) {
        User u = load(username);
        return sources.stream().filter(s -> slug.equals(s.slug())).findFirst()
            .map(s -> s.build(u))
            .orElseGet(() -> new Report(slug, title, subtitleFor(u), columns, List.of()));
    }

    @Transactional(readOnly = true)
    public Report profile(String username) {
        User u = load(username);
        List<List<String>> rows = List.of(
            List.of("StockSim user ID", String.valueOf(u.getPublicUserId())),
            List.of("Name", u.getName()),
            List.of("Username", u.getUsername()),
            List.of("Email", u.getEmail()),
            List.of("Email verified", u.isEmailVerifiedOrLegacy() ? "Yes" : "No"),
            List.of("Member since", u.getCreatedAt() == null ? "" : u.getCreatedAt().toString().replace('T', ' ')));
        return new Report("profile", "Profile summary", subtitleFor(u), List.of("Field", "Value"), rows);
    }

    /** Top-up statement from the payments table. */
    @Transactional(readOnly = true)
    public Report transactions(String username) {
        User u = load(username);
        List<List<String>> rows = new ArrayList<>();
        for (int page = 0; rows.size() < MAX_ROWS; page++) {
            Page<PaymentTransaction> batch = payments.findByUserOrderByCreatedAtDescIdDesc(u, PageRequest.of(page, PAGE_SIZE));
            for (PaymentTransaction p : batch.getContent()) {
                rows.add(List.of(
                    STAMP.format(p.getCreatedAt()),
                    String.valueOf(p.getId()),
                    p.getAmount().toPlainString(),
                    p.getStatus().name(),
                    p.getFailureReason() == null ? "Simulated top-up" : p.getFailureReason()));
                if (rows.size() >= MAX_ROWS) break;
            }
            if (!batch.hasNext()) break;
        }
        return new Report("transactions", "Top-up statement (simulated)", subtitleFor(u),
            List.of("Date (UTC)", "Payment ID", "Amount (INR)", "Status", "Note"), rows);
    }

    public Rendered render(Report report, String format) {
        String f = format == null ? "" : format.trim().toLowerCase();
        String day = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
        String base = "stocksim-" + report.slug() + "-" + day;
        switch (f) {
            case "csv":
                return new Rendered(CsvWriter.write(report.columns(), report.rows()),
                    "text/csv;charset=UTF-8", base + ".csv");
            case "pdf":
                try {
                    return new Rendered(PdfReportBuilder.build(report.title(), report.subtitle(), report.columns(), report.rows()),
                        "application/pdf", base + ".pdf");
                } catch (IOException e) {
                    throw new IllegalStateException("Could not build the PDF report", e);
                }
            default:
                throw new IllegalArgumentException("format must be csv or pdf");
        }
    }

    public static String subtitleFor(User u) {
        return "User ID " + u.getPublicUserId() + "  |  Generated " + STAMP.format(Instant.now()) + " UTC";
    }

    private User load(String username) {
        return users.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
