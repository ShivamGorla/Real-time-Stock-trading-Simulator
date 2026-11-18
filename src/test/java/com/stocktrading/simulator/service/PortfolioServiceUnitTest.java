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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortfolioService Unit Tests")
class PortfolioServiceUnitTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private HoldingRepository holdingRepository;

    private PortfolioService portfolioService;

    private User testUser;
    private Wallet testWallet;
    private Stock stockInfy;
    private Stock stockReliance;

    @BeforeEach
    void setUp() {
        portfolioService = new PortfolioService(userRepository, walletRepository, holdingRepository);

        testUser = new User("Alice", "alice", "alice@example.com", "hash");
        ReflectionTestUtils.setField(testUser, "id", 1L);

        testWallet = new Wallet(testUser, new BigDecimal("100000.00"), "INR");
        ReflectionTestUtils.setField(testWallet, "id", 10L);

        stockInfy = new Stock("INFY", "Infosys Limited", new BigDecimal("1550.00"));
        ReflectionTestUtils.setField(stockInfy, "id", 101L);

        stockReliance = new Stock("RELIANCE", "Reliance Industries Limited", new BigDecimal("2950.00"));
        ReflectionTestUtils.setField(stockReliance, "id", 102L);
    }

    @Test
    @DisplayName("1. Empty portfolio: Returns zero valuation metrics, accurate wallet equity, and empty holdings list")
    void getPortfolio_EmptyPortfolio_ReturnsZeroMetricsAndEmptyList() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of());

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        assertNotNull(response);
        assertEquals(1L, response.getUserId());
        assertEquals("alice", response.getUsername());
        assertEquals(new BigDecimal("100000.00"), response.getWalletBalance());
        assertEquals("INR", response.getCurrency());
        assertEquals(new BigDecimal("0.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("0.00"), response.getTotalCurrentMarketValue());
        assertEquals(new BigDecimal("0.00"), response.getTotalUnrealizedPnl());
        assertEquals(new BigDecimal("0.00"), response.getTotalRealizedPnl());
        assertEquals(new BigDecimal("0.00"), response.getTotalPnl());
        assertEquals(new BigDecimal("0.00"), response.getUnrealizedReturnPercentage());
        assertEquals(new BigDecimal("100000.00"), response.getTotalAccountEquity());
        assertNotNull(response.getGeneratedAt());
        assertTrue(response.getHoldings().isEmpty());
    }

    @Test
    @DisplayName("2. One active holding: Correctly calculates invested value, market value, and unrealized P&L")
    void getPortfolio_OneActiveHolding_ComputesCorrectMetrics() {
        // Holding: 10 INFY @ 1500.000000, current price 1550.00
        Holding holding = new Holding(testUser, stockInfy, 10, new BigDecimal("1500.000000"), BigDecimal.ZERO);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(holding));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        assertEquals(new BigDecimal("15000.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("15500.00"), response.getTotalCurrentMarketValue());
        assertEquals(new BigDecimal("500.00"), response.getTotalUnrealizedPnl());
        assertEquals(new BigDecimal("0.00"), response.getTotalRealizedPnl());
        assertEquals(new BigDecimal("500.00"), response.getTotalPnl());
        // 500 * 100 / 15000 = 3.33%
        assertEquals(new BigDecimal("3.33"), response.getUnrealizedReturnPercentage());
        assertEquals(new BigDecimal("115500.00"), response.getTotalAccountEquity());

        assertEquals(1, response.getHoldings().size());
        HoldingResponse hr = response.getHoldings().get(0);
        assertEquals("INFY", hr.getSymbol());
        assertEquals(10, hr.getQuantity());
        assertEquals(new BigDecimal("1500.000000"), hr.getAverageCost());
        assertEquals(new BigDecimal("1550.00"), hr.getCurrentPrice());
        assertEquals(new BigDecimal("15000.00"), hr.getInvestedValue());
        assertEquals(new BigDecimal("15500.00"), hr.getCurrentMarketValue());
        assertEquals(new BigDecimal("500.00"), hr.getUnrealizedPnl());
        assertEquals(new BigDecimal("3.33"), hr.getUnrealizedReturnPercentage());
    }

    @Test
    @DisplayName("3 & 7. Multiple active holdings: Correctly aggregates across positions")
    void getPortfolio_MultipleHoldings_AggregatesAccurately() {
        Holding h1 = new Holding(testUser, stockInfy, 2, new BigDecimal("1550.000000"), BigDecimal.ZERO);
        Holding h2 = new Holding(testUser, stockReliance, 1, new BigDecimal("2950.000000"), BigDecimal.ZERO);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(h1, h2));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        // h1 invested = 3100.00, market = 3100.00
        // h2 invested = 2950.00, market = 2950.00
        // totalInvested = 6050.00, totalMarket = 6050.00
        assertEquals(new BigDecimal("6050.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("6050.00"), response.getTotalCurrentMarketValue());
        assertEquals(new BigDecimal("0.00"), response.getTotalUnrealizedPnl());
        assertEquals(new BigDecimal("0.00"), response.getUnrealizedReturnPercentage());
        assertEquals(new BigDecimal("106050.00"), response.getTotalAccountEquity());
        assertEquals(2, response.getHoldings().size());
    }

    @Test
    @DisplayName("4. Profitable position: Calculates positive return percentage and unrealized gain")
    void getPortfolio_ProfitablePosition_ComputesPositiveGain() {
        stockInfy.updatePrice(new BigDecimal("2000.00")); // price went up from 1500 to 2000
        Holding holding = new Holding(testUser, stockInfy, 10, new BigDecimal("1500.000000"), BigDecimal.ZERO);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(holding));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        assertEquals(new BigDecimal("15000.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("20000.00"), response.getTotalCurrentMarketValue());
        assertEquals(new BigDecimal("5000.00"), response.getTotalUnrealizedPnl());
        // 5000 * 100 / 15000 = 33.33%
        assertEquals(new BigDecimal("33.33"), response.getUnrealizedReturnPercentage());
    }

    @Test
    @DisplayName("5. Loss-making position: Calculates negative return percentage and unrealized loss")
    void getPortfolio_LossMakingPosition_ComputesNegativeReturn() {
        stockInfy.updatePrice(new BigDecimal("1200.00")); // price went down from 1500 to 1200
        Holding holding = new Holding(testUser, stockInfy, 10, new BigDecimal("1500.000000"), BigDecimal.ZERO);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(holding));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        assertEquals(new BigDecimal("15000.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("12000.00"), response.getTotalCurrentMarketValue());
        assertEquals(new BigDecimal("-3000.00"), response.getTotalUnrealizedPnl());
        // -3000 * 100 / 15000 = -20.00%
        assertEquals(new BigDecimal("-20.00"), response.getUnrealizedReturnPercentage());
    }

    @Test
    @DisplayName("6. Zero invested value: Avoids division by zero and returns 0.00 percentage")
    void getPortfolio_ZeroInvestedValue_ReturnsZeroPercentage() {
        // Zero quantity holding
        Holding closedHolding = new Holding(testUser, stockInfy, 0, new BigDecimal("1500.000000"), new BigDecimal("250.00"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(closedHolding));

        PortfolioResponse response = portfolioService.getPortfolio("alice", true);

        assertEquals(new BigDecimal("0.00"), response.getTotalInvestedValue());
        assertEquals(new BigDecimal("0.00"), response.getUnrealizedReturnPercentage());
        assertEquals(new BigDecimal("250.00"), response.getTotalRealizedPnl());

        HoldingResponse hr = response.getHoldings().get(0);
        assertEquals(0, hr.getQuantity());
        assertEquals(new BigDecimal("0.00"), hr.getInvestedValue());
        assertEquals(new BigDecimal("0.00"), hr.getUnrealizedReturnPercentage());
    }

    @Test
    @DisplayName("8 & 9. Closed holding: Excluded by default, included when includeClosed=true")
    void getPortfolio_ClosedHolding_Filtering() {
        Holding openHolding = new Holding(testUser, stockInfy, 5, new BigDecimal("1550.000000"), BigDecimal.ZERO);
        Holding closedHolding = new Holding(testUser, stockReliance, 0, new BigDecimal("2950.000000"), new BigDecimal("300.00"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(openHolding, closedHolding));

        // Default: includeClosed = false
        PortfolioResponse respDefault = portfolioService.getPortfolio("alice", false);
        assertEquals(1, respDefault.getHoldings().size());
        assertEquals("INFY", respDefault.getHoldings().get(0).getSymbol());

        // Explicit: includeClosed = true
        PortfolioResponse respWithClosed = portfolioService.getPortfolio("alice", true);
        assertEquals(2, respWithClosed.getHoldings().size());
        assertEquals("INFY", respWithClosed.getHoldings().get(0).getSymbol());
        assertEquals("RELIANCE", respWithClosed.getHoldings().get(1).getSymbol());
        assertEquals(0, respWithClosed.getHoldings().get(1).getQuantity());
        assertEquals(new BigDecimal("300.00"), respWithClosed.getHoldings().get(1).getCumulativeRealizedPnl());
    }

    @Test
    @DisplayName("10 & 11. Closed holding realized P&L: Included in summary; summary invariant under includeClosed toggle")
    void getPortfolio_SummaryMetrics_InvariantUnderIncludeClosedToggle() {
        Holding openHolding = new Holding(testUser, stockInfy, 5, new BigDecimal("1550.000000"), new BigDecimal("100.00"));
        Holding closedHolding = new Holding(testUser, stockReliance, 0, new BigDecimal("2950.000000"), new BigDecimal("400.00"));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(openHolding, closedHolding));

        PortfolioResponse rFalse = portfolioService.getPortfolio("alice", false);
        PortfolioResponse rTrue = portfolioService.getPortfolio("alice", true);

        // Both must report identical total realized P&L: 100 + 400 = 500.00
        assertEquals(new BigDecimal("500.00"), rFalse.getTotalRealizedPnl());
        assertEquals(new BigDecimal("500.00"), rTrue.getTotalRealizedPnl());

        // Both must report identical summary metrics
        assertEquals(rFalse.getTotalInvestedValue(), rTrue.getTotalInvestedValue());
        assertEquals(rFalse.getTotalCurrentMarketValue(), rTrue.getTotalCurrentMarketValue());
        assertEquals(rFalse.getTotalUnrealizedPnl(), rTrue.getTotalUnrealizedPnl());
        assertEquals(rFalse.getTotalPnl(), rTrue.getTotalPnl());
        assertEquals(rFalse.getTotalAccountEquity(), rTrue.getTotalAccountEquity());
        assertEquals(rFalse.getUnrealizedReturnPercentage(), rTrue.getUnrealizedReturnPercentage());

        // Only the holdings array size differs
        assertEquals(1, rFalse.getHoldings().size());
        assertEquals(2, rTrue.getHoldings().size());
    }

    @Test
    @DisplayName("12. Alphabetical ordering: Returns holdings sorted by symbol")
    void getPortfolio_AlphabeticalOrdering() {
        Holding h1 = new Holding(testUser, stockReliance, 1, new BigDecimal("2950.000000"), BigDecimal.ZERO);
        Holding h2 = new Holding(testUser, stockInfy, 1, new BigDecimal("1550.000000"), BigDecimal.ZERO);

        // HoldingRepository returns them sorted by symbol ASC: INFY then RELIANCE
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(h2, h1));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        assertEquals("INFY", response.getHoldings().get(0).getSymbol());
        assertEquals("RELIANCE", response.getHoldings().get(1).getSymbol());
    }

    @Test
    @DisplayName("13. Missing user: Throws IllegalArgumentException")
    void getPortfolio_MissingUser_ThrowsException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
            portfolioService.getPortfolio("unknown", false)
        );
    }

    @Test
    @DisplayName("14. Missing wallet: Throws IllegalStateException")
    void getPortfolio_MissingWallet_ThrowsException() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
            portfolioService.getPortfolio("alice", false)
        );
    }

    @Test
    @DisplayName("15. BigDecimal scale and rounding: Exact 2-decimal and 6-decimal precision")
    void getPortfolio_BigDecimalPrecisionAndRounding() {
        // Average cost: 1550.123456, Quantity: 3
        // 3 * 1550.123456 = 4650.370368 -> scale 2 HALF_UP: 4650.37
        Holding holding = new Holding(testUser, stockInfy, 3, new BigDecimal("1550.123456"), BigDecimal.ZERO);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(testWallet));
        when(holdingRepository.findByUserIdWithStock(1L)).thenReturn(List.of(holding));

        PortfolioResponse response = portfolioService.getPortfolio("alice", false);

        HoldingResponse hr = response.getHoldings().get(0);
        assertEquals(new BigDecimal("1550.123456"), hr.getAverageCost());
        assertEquals(new BigDecimal("4650.37"), hr.getInvestedValue());
        // Current price: 1550.00 * 3 = 4650.00
        assertEquals(new BigDecimal("4650.00"), hr.getCurrentMarketValue());
        // Unrealized P&L: 4650.00 - 4650.37 = -0.37
        assertEquals(new BigDecimal("-0.37"), hr.getUnrealizedPnl());
    }
}
