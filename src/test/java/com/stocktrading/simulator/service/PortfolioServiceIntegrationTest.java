package com.stocktrading.simulator.service;

import com.stocktrading.simulator.dto.OrderRequest;
import com.stocktrading.simulator.dto.OrderResponse;
import com.stocktrading.simulator.dto.PortfolioResponse;
import com.stocktrading.simulator.model.*;
import com.stocktrading.simulator.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("PortfolioService Integration Tests")
class PortfolioServiceIntegrationTest {

    @Autowired
    private PortfolioService portfolioService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderExecutionService orderExecutionService;

    @Autowired
    private StockService stockService;

    @Autowired
    private WalletService walletService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockPriceHistoryRepository historyRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    private User userA;
    private User userB;
    private Stock stockInfy;
    private Stock stockReliance;

    @BeforeEach
    void setUp() {
        cleanup();

        userA = userRepository.save(new User("Alpha User", "alpha", "alpha@example.com", "hashA"));
        walletService.createWalletWithInitialBalance(userA); // 100,000 INR

        userB = userRepository.save(new User("Beta User", "beta", "beta@example.com", "hashB"));
        walletService.createWalletWithInitialBalance(userB);

        stockInfy = stockService.createStockIfAbsent("INFY", "Infosys Limited", new BigDecimal("1550.00"));
        stockReliance = stockService.createStockIfAbsent("RELIANCE", "Reliance Industries Limited", new BigDecimal("2950.00"));
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        tradeRepository.deleteAll();
        orderRepository.deleteAll();
        holdingRepository.deleteAll();
        walletTransactionRepository.deleteAll();
        walletRepository.deleteAll();
        historyRepository.deleteAll();
        stockRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void executeBuy(String username, String symbol, int qty) {
        OrderResponse order = orderService.createBuyOrder(
                username,
                new OrderRequest(UUID.randomUUID().toString(), symbol, qty)
        );
        orderExecutionService.executeOrder(order.getId(), username);
    }

    private void executeSell(String username, String symbol, int qty) {
        OrderResponse order = orderService.createSellOrder(
                username,
                new OrderRequest(UUID.randomUUID().toString(), symbol, qty)
        );
        orderExecutionService.executeOrder(order.getId(), username);
    }

    @Test
    @DisplayName("1. New user with initial wallet and no holdings: Zero portfolio metrics and exact account equity")
    void getPortfolio_NewUser_AccurateInitialState() {
        PortfolioResponse portfolio = portfolioService.getPortfolio("alpha", false);

        assertNotNull(portfolio);
        assertEquals(userA.getId(), portfolio.getUserId());
        assertEquals("alpha", portfolio.getUsername());
        assertEquals(0, new BigDecimal("100000.00").compareTo(portfolio.getWalletBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalInvestedValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalCurrentMarketValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalUnrealizedPnl()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalRealizedPnl()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalPnl()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(portfolio.getTotalAccountEquity()));
        assertTrue(portfolio.getHoldings().isEmpty());
    }

    @Test
    @DisplayName("2. Portfolio after BUY: Reflects debited wallet, position invested value, and dynamic stock price")
    void getPortfolio_AfterBuy_ReflectsAccurateValuation() {
        // Buy 10 INFY @ 1550.00 = 15,500.00 INR
        executeBuy("alpha", "INFY", 10);

        PortfolioResponse portfolio = portfolioService.getPortfolio("alpha", false);

        // Wallet: 100,000 - 15,500 = 84,500.00
        assertEquals(0, new BigDecimal("84500.00").compareTo(portfolio.getWalletBalance()));
        assertEquals(0, new BigDecimal("15500.00").compareTo(portfolio.getTotalInvestedValue()));
        assertEquals(0, new BigDecimal("15500.00").compareTo(portfolio.getTotalCurrentMarketValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalUnrealizedPnl()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(portfolio.getTotalAccountEquity()));

        assertEquals(1, portfolio.getHoldings().size());
        var h = portfolio.getHoldings().get(0);
        assertEquals("INFY", h.getSymbol());
        assertEquals(10, h.getQuantity());
        assertEquals(0, new BigDecimal("1550.000000").compareTo(h.getAverageCost()));
        assertEquals(0, new BigDecimal("1550.00").compareTo(h.getCurrentPrice()));
        assertEquals(0, new BigDecimal("15500.00").compareTo(h.getInvestedValue()));
        assertEquals(0, new BigDecimal("15500.00").compareTo(h.getCurrentMarketValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(h.getUnrealizedPnl()));
    }

    @Test
    @DisplayName("3. Portfolio after multiple BUY operations: Tracks weighted-average cost correctly")
    void getPortfolio_AfterMultipleBuys_TracksWeightedAverageCost() {
        // Buy 1: 10 INFY @ 1550.00 = 15,500.00
        executeBuy("alpha", "INFY", 10);

        // Price changes to 1650.00
        stockService.updateStockPrice("INFY", new BigDecimal("1650.00"));

        // Buy 2: 10 INFY @ 1650.00 = 16,500.00
        executeBuy("alpha", "INFY", 10);

        PortfolioResponse portfolio = portfolioService.getPortfolio("alpha", false);

        // Total 20 shares, total cost 32,000 -> WAC = 1600.000000
        // Current market price: 1650.00 -> Market value = 33,000.00
        // Total invested = 32,000.00
        // Total unrealized P&L = +1000.00
        assertEquals(0, new BigDecimal("32000.00").compareTo(portfolio.getTotalInvestedValue()));
        assertEquals(0, new BigDecimal("33000.00").compareTo(portfolio.getTotalCurrentMarketValue()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(portfolio.getTotalUnrealizedPnl()));
        // Unrealized return: 1000 * 100 / 32000 = 3.13%
        assertEquals(new BigDecimal("3.13"), portfolio.getUnrealizedReturnPercentage());
    }

    @Test
    @DisplayName("4. Portfolio after partial SELL: Reflects remaining shares, updated wallet, and realized P&L")
    void getPortfolio_AfterPartialSell_ReflectsRealizedAndUnrealized() {
        // Buy 10 INFY @ 1550 = 15,500
        executeBuy("alpha", "INFY", 10);

        // Price rises to 1750
        stockService.updateStockPrice("INFY", new BigDecimal("1750.00"));

        // Sell 4 INFY @ 1750 = 7,000. Proceeds = 7,000. Realized P&L = 4 * 200 = +800.00
        executeSell("alpha", "INFY", 4);

        PortfolioResponse portfolio = portfolioService.getPortfolio("alpha", false);

        // Wallet: 100,000 - 15,500 + 7,000 = 91,500.00
        assertEquals(0, new BigDecimal("91500.00").compareTo(portfolio.getWalletBalance()));
        // Remaining 6 shares @ 1550 = 9,300.00 invested
        assertEquals(0, new BigDecimal("9300.00").compareTo(portfolio.getTotalInvestedValue()));
        // Current market value: 6 * 1750 = 10,500.00
        assertEquals(0, new BigDecimal("10500.00").compareTo(portfolio.getTotalCurrentMarketValue()));
        // Unrealized P&L: 10,500 - 9,300 = +1,200.00
        assertEquals(0, new BigDecimal("1200.00").compareTo(portfolio.getTotalUnrealizedPnl()));
        // Realized P&L = +800.00
        assertEquals(0, new BigDecimal("800.00").compareTo(portfolio.getTotalRealizedPnl()));
        // Total P&L = 800 + 1200 = +2,000.00
        assertEquals(0, new BigDecimal("2000.00").compareTo(portfolio.getTotalPnl()));
        // Total account equity = 91,500 + 10,500 = 102,000.00
        assertEquals(0, new BigDecimal("102000.00").compareTo(portfolio.getTotalAccountEquity()));
    }

    @Test
    @DisplayName("5. Portfolio after completely closing a holding: Zero invested/market value, historical realized P&L retained")
    void getPortfolio_AfterCompleteLiquidation_RetainsRealizedPnl() {
        // Buy 10 INFY @ 1550 = 15,500
        executeBuy("alpha", "INFY", 10);

        // Price rises to 1800
        stockService.updateStockPrice("INFY", new BigDecimal("1800.00"));

        // Sell all 10 INFY @ 1800 = 18,000. Gain = 10 * 250 = +2500.00
        executeSell("alpha", "INFY", 10);

        PortfolioResponse portfolio = portfolioService.getPortfolio("alpha", false);

        // Wallet: 100,000 - 15,500 + 18,000 = 102,500.00
        assertEquals(0, new BigDecimal("102500.00").compareTo(portfolio.getWalletBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalInvestedValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalCurrentMarketValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(portfolio.getTotalUnrealizedPnl()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(portfolio.getTotalRealizedPnl()));
        assertEquals(0, new BigDecimal("2500.00").compareTo(portfolio.getTotalPnl()));
        assertEquals(0, new BigDecimal("102500.00").compareTo(portfolio.getTotalAccountEquity()));
        assertTrue(portfolio.getHoldings().isEmpty());
    }

    @Test
    @DisplayName("6. Cross-user isolation: User A and User B cannot view each other's holdings or balances")
    void getPortfolio_CrossUserIsolation() {
        executeBuy("alpha", "INFY", 10);
        executeBuy("beta", "RELIANCE", 2);

        PortfolioResponse portfolioA = portfolioService.getPortfolio("alpha", false);
        PortfolioResponse portfolioB = portfolioService.getPortfolio("beta", false);

        assertEquals(1, portfolioA.getHoldings().size());
        assertEquals("INFY", portfolioA.getHoldings().get(0).getSymbol());

        assertEquals(1, portfolioB.getHoldings().size());
        assertEquals("RELIANCE", portfolioB.getHoldings().get(0).getSymbol());
    }

    @Test
    @DisplayName("7. includeClosed toggle: Changes returned rows but leaves summary metrics strictly invariant")
    void getPortfolio_IncludeClosedToggle_LeavesSummaryMetricsInvariant() {
        executeBuy("alpha", "INFY", 10);
        executeBuy("alpha", "RELIANCE", 2);

        // Close RELIANCE
        executeSell("alpha", "RELIANCE", 2);

        PortfolioResponse pWithoutClosed = portfolioService.getPortfolio("alpha", false);
        PortfolioResponse pWithClosed = portfolioService.getPortfolio("alpha", true);

        assertEquals(1, pWithoutClosed.getHoldings().size());
        assertEquals(2, pWithClosed.getHoldings().size());

        assertEquals(pWithoutClosed.getTotalInvestedValue(), pWithClosed.getTotalInvestedValue());
        assertEquals(pWithoutClosed.getTotalCurrentMarketValue(), pWithClosed.getTotalCurrentMarketValue());
        assertEquals(pWithoutClosed.getTotalUnrealizedPnl(), pWithClosed.getTotalUnrealizedPnl());
        assertEquals(pWithoutClosed.getTotalRealizedPnl(), pWithClosed.getTotalRealizedPnl());
        assertEquals(pWithoutClosed.getTotalPnl(), pWithClosed.getTotalPnl());
        assertEquals(pWithoutClosed.getTotalAccountEquity(), pWithClosed.getTotalAccountEquity());
        assertEquals(pWithoutClosed.getUnrealizedReturnPercentage(), pWithClosed.getUnrealizedReturnPercentage());
    }
}
