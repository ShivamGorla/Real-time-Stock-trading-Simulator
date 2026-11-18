package com.stocktrading.simulator.controller;

import com.stocktrading.simulator.service.ReportService;
import com.stocktrading.simulator.service.ReportService.Rendered;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Downloads of the caller's own data. Login is enforced in SecurityConfig; no user id is ever accepted. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/profile")
    public ResponseEntity<byte[]> profile(@RequestParam(defaultValue = "csv") String format, Authentication auth) {
        return respond(reports.render(reports.profile(auth.getName()), format));
    }

    @GetMapping("/transactions")
    public ResponseEntity<byte[]> transactions(@RequestParam(defaultValue = "csv") String format, Authentication auth) {
        return respond(reports.render(reports.transactions(auth.getName()), format));
    }

    @GetMapping("/trading-history")
    public ResponseEntity<byte[]> tradingHistory(@RequestParam(defaultValue = "csv") String format, Authentication auth) {
        return respond(reports.render(reports.tradingHistory(auth.getName()), format));
    }

    @GetMapping("/portfolio")
    public ResponseEntity<byte[]> portfolio(@RequestParam(defaultValue = "csv") String format, Authentication auth) {
        return respond(reports.render(reports.portfolio(auth.getName()), format));
    }

    private ResponseEntity<byte[]> respond(Rendered r) {
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(r.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(r.filename()).build().toString())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(r.body());
    }
}
