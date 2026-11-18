package com.stocktrading.simulator.model;

import com.stocktrading.simulator.exception.InsufficientHoldingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Holding Model Unit Tests")
class HoldingTest {

    private User testUser;
    private Stock testStock;
    private Holding holding;

    @BeforeEach
    void setUp() {
        testUser = new User("Alice", "alice", "alice@example.com", "hash");
        ReflectionTestUtils.setField(testUser, "id", 1L);

        testStock = new Stock("AAPL", "Apple Inc.", new BigDecimal("150.00"));
        ReflectionTestUtils.setField(testStock, "id", 100L);

        holding = new Holding(testUser, testStock);
    }

    @Test
    @DisplayName("Initial holding starts with quantity 0, averageCost 0, and totalRealizedPnl 0.00")
    void initialHolding_ZeroValues() {
        assertEquals(0, holding.getQuantity());
        assertEquals(new BigDecimal("0.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
        assertEquals(testUser, holding.getUser());
        assertEquals(testStock, holding.getStock());
    }

    @Test
    @DisplayName("First BUY: Correctly initializes quantity and average cost")
    void firstBuy_InitializesPosition() {
        holding.recordBuy(10, new BigDecimal("100.00"));

        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Multiple BUYs: Calculates weighted-average cost correctly (WAC)")
    void multipleBuys_WeightedAverageCost() {
        // Buy 1: 10 @ 100.00 (Total: 1000)
        holding.recordBuy(10, new BigDecimal("100.00"));
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());

        // Buy 2: 20 @ 130.00 (Total: 1000 + 2600 = 3600 / 30 = 120.00)
        holding.recordBuy(20, new BigDecimal("130.00"));
        assertEquals(30, holding.getQuantity());
        assertEquals(new BigDecimal("120.000000"), holding.getAverageCost());

        // Buy 3: 10 @ 150.00 (Total: 3600 + 1500 = 5100 / 40 = 127.50)
        holding.recordBuy(10, new BigDecimal("150.00"));
        assertEquals(40, holding.getQuantity());
        assertEquals(new BigDecimal("127.500000"), holding.getAverageCost());
    }

    @Test
    @DisplayName("SELL with profit: Updates quantity and accumulates realized P&L")
    void sell_WithProfit() {
        holding.recordBuy(20, new BigDecimal("100.00"));

        // Sell 10 @ 150.00 (Cost basis: 100.00, gain: 50.00 per share * 10 = 500.00)
        BigDecimal realizedPnl = holding.recordSell(10, new BigDecimal("150.00"));

        assertEquals(new BigDecimal("500.00"), realizedPnl);
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("500.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("SELL with loss: Correctly handles negative realized P&L (T8)")
    void sell_WithLoss() {
        holding.recordBuy(20, new BigDecimal("100.00"));

        // Sell 10 @ 75.00 (Cost basis: 100.00, loss: -25.00 per share * 10 = -250.00)
        BigDecimal realizedPnl = holding.recordSell(10, new BigDecimal("75.00"));

        assertEquals(new BigDecimal("-250.00"), realizedPnl);
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("-250.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Closing position retains cumulative realized P&L (T10)")
    void closePosition_RetainsCumulativeRealizedPnl() {
        holding.recordBuy(10, new BigDecimal("100.00"));
        holding.recordSell(10, new BigDecimal("125.00")); // +250.00 gain

        assertEquals(0, holding.getQuantity());
        assertEquals(new BigDecimal("250.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Reopening position resets average cost directly to new buy price without erasing historical P&L (T10)")
    void reopeningPosition_ResetsAverageCostPreservingPnl() {
        holding.recordBuy(10, new BigDecimal("100.00"));
        holding.recordSell(10, new BigDecimal("150.00")); // +500.00 gain, closed

        assertEquals(0, holding.getQuantity());
        assertEquals(new BigDecimal("500.00"), holding.getTotalRealizedPnl());

        // Reopen with new buy of 5 @ 200.00
        holding.recordBuy(5, new BigDecimal("200.00"));

        assertEquals(5, holding.getQuantity());
        assertEquals(new BigDecimal("200.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("500.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Selling more shares than held throws exception without mutating entity")
    void sell_InsufficientQuantity_ThrowsException() {
        holding.recordBuy(10, new BigDecimal("100.00"));

        InsufficientHoldingException ex = assertThrows(InsufficientHoldingException.class, () ->
            holding.recordSell(15, new BigDecimal("120.00"))
        );

        assertTrue(ex.getMessage().contains("Insufficient shares: requested 15, currently holding 10"));
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Zero and negative quantities are rejected")
    void zeroAndNegativeQuantities_Rejected() {
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordBuy(0, new BigDecimal("100.00"))
        );
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordBuy(-5, new BigDecimal("100.00"))
        );
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordBuy(null, new BigDecimal("100.00"))
        );

        holding.recordBuy(10, new BigDecimal("100.00"));

        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(0, new BigDecimal("100.00"))
        );
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(-5, new BigDecimal("100.00"))
        );
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(null, new BigDecimal("100.00"))
        );
    }

    @Test
    @DisplayName("Maximum stock price in BUY is accepted; above maximum is rejected")
    void buyPrice_Boundaries() {
        holding.recordBuy(1, new BigDecimal("999999999.99"));
        assertEquals(new BigDecimal("999999999.990000"), holding.getAverageCost());

        assertThrows(IllegalArgumentException.class, () ->
            holding.recordBuy(1, new BigDecimal("1000000000.00"))
        );
    }

    @Test
    @DisplayName("Maximum stock price in SELL is accepted; above maximum is rejected")
    void sellPrice_Boundaries() {
        holding.recordBuy(1, new BigDecimal("100.00"));

        holding.recordSell(1, new BigDecimal("999999999.99"));
        assertEquals(new BigDecimal("999999899.99"), holding.getTotalRealizedPnl());

        holding.recordBuy(1, new BigDecimal("100.00"));
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(1, new BigDecimal("1000000000.00"))
        );
    }

    @Test
    @DisplayName("Realized P&L boundary validation prevents values exceeding DECIMAL(15,2) storage range")
    void realizedPnl_Boundaries() {
        holding.recordBuy(10, new BigDecimal("100.00"));
        // Force totalRealizedPnl close to max
        ReflectionTestUtils.setField(holding, "totalRealizedPnl", new BigDecimal("9999999999990.00"));

        // Selling at huge profit that exceeds 9999999999999.99
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(1, new BigDecimal("100000.00"))
        );
    }

    @Test
    @DisplayName("Quantity overflow triggers ArithmeticException directly (T3)")
    void quantityOverflow_ThrowsArithmeticException() {
        // Set holding to Integer.MAX_VALUE
        ReflectionTestUtils.setField(holding, "quantity", Integer.MAX_VALUE);
        ReflectionTestUtils.setField(holding, "averageCost", new BigDecimal("50.000000"));

        assertThrows(ArithmeticException.class, () ->
            holding.recordBuy(1, new BigDecimal("50.00"))
        );

        // Verify entity state remained completely unmutated
        assertEquals(Integer.MAX_VALUE, holding.getQuantity());
        assertEquals(new BigDecimal("50.000000"), holding.getAverageCost());
    }

    @Test
    @DisplayName("Failed calculation leaves entity completely unmutated (T11)")
    void failedCalculation_LeavesEntityUnchanged() {
        holding.recordBuy(10, new BigDecimal("100.00"));

        // Attempt sell with invalid price (below 0.01)
        assertThrows(IllegalArgumentException.class, () ->
            holding.recordSell(5, new BigDecimal("0.004"))
        );

        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("Constructor parameter validations")
    void constructorValidations() {
        assertThrows(IllegalArgumentException.class, () -> new Holding(null, testStock));
        assertThrows(IllegalArgumentException.class, () -> new Holding(testUser, null));
    }

    @Test
    @DisplayName("previewBuy: Computes correct quantity and WAC without mutating original holding")
    void previewBuy_ComputesCorrectState_WithoutMutatingOriginalHolding() {
        holding.recordBuy(10, new BigDecimal("100.00")); // initial position: 10 @ 100

        Holding.BuyCalculation calc = holding.previewBuy(10, new BigDecimal("200.00"));

        // Verify calculation output: 20 total shares, average cost 150.000000
        assertEquals(20, calc.newQuantity());
        assertEquals(new BigDecimal("150.000000"), calc.newAverageCost());

        // Verify original holding remains completely untouched
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("previewBuy: Integer overflow triggers ArithmeticException without mutating holding")
    void previewBuy_IntegerOverflow_ThrowsArithmeticException() {
        ReflectionTestUtils.setField(holding, "quantity", Integer.MAX_VALUE);

        assertThrows(ArithmeticException.class, () ->
            holding.previewBuy(1, new BigDecimal("50.00"))
        );

        assertEquals(Integer.MAX_VALUE, holding.getQuantity());
    }

    @Test
    @DisplayName("applyBuy: Correctly applies pre-calculated BuyCalculation")
    void applyBuy_AppliesCalculatedState() {
        Holding.BuyCalculation calc = new Holding.BuyCalculation(25, new BigDecimal("175.500000"));
        holding.applyBuy(calc);

        assertEquals(25, holding.getQuantity());
        assertEquals(new BigDecimal("175.500000"), holding.getAverageCost());
        assertNotNull(holding.getUpdatedAt());
    }

    @Test
    @DisplayName("previewSell: Computes correct remaining quantity and realized P&L without mutating original holding")
    void previewSell_ComputesCorrectState_WithoutMutatingOriginalHolding() {
        holding.recordBuy(10, new BigDecimal("100.00"));

        Holding.SellCalculation calc = holding.previewSell(4, new BigDecimal("130.00"));

        // Verify calculation output: 6 shares remaining, order PnL +120.00, cumulative PnL +120.00
        assertEquals(6, calc.remainingQuantity());
        assertEquals(new BigDecimal("120.00"), calc.orderRealizedPnl());
        assertEquals(new BigDecimal("120.00"), calc.newCumulativeRealizedPnl());

        // Verify original holding remains completely untouched
        assertEquals(10, holding.getQuantity());
        assertEquals(new BigDecimal("100.000000"), holding.getAverageCost());
        assertEquals(new BigDecimal("0.00"), holding.getTotalRealizedPnl());
    }

    @Test
    @DisplayName("previewSell: Insufficient quantity throws InsufficientHoldingException without mutating holding")
    void previewSell_InsufficientQuantity_ThrowsException() {
        holding.recordBuy(5, new BigDecimal("100.00"));

        assertThrows(InsufficientHoldingException.class, () ->
            holding.previewSell(10, new BigDecimal("120.00"))
        );

        assertEquals(5, holding.getQuantity());
    }

    @Test
    @DisplayName("applySell: Correctly applies pre-calculated SellCalculation")
    void applySell_AppliesCalculatedState() {
        holding.recordBuy(10, new BigDecimal("100.00"));
        Holding.SellCalculation calc = new Holding.SellCalculation(
            new BigDecimal("150.00"),
            new BigDecimal("150.00"),
            5
        );

        holding.applySell(calc);

        assertEquals(5, holding.getQuantity());
        assertEquals(new BigDecimal("150.00"), holding.getTotalRealizedPnl());
        assertNotNull(holding.getUpdatedAt());
    }
}
