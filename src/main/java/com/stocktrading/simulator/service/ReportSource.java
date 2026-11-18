package com.stocktrading.simulator.service;

import com.stocktrading.simulator.model.User;

/**
 * Plug-in point for report data owned by other modules.
 * Slugs: "trading-history" (GET /api/reports/trading-history), "portfolio" (GET /api/reports/portfolio).
 * Implementations must only return data for the given user.
 */
public interface ReportSource {

    String slug();

    ReportService.Report build(User user);
}
