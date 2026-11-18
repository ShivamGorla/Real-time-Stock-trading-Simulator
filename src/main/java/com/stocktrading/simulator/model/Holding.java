package com.stocktrading.simulator.model;

import com.stocktrading.simulator.exception.InsufficientHoldingException;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Domain entity representing a user's stock position holding.
 *
 * <p>Maintains aggregate position quantity, weighted-average cost basis (DECIMAL(19,6)),
 * and cumulative realized profit/loss across position lifecycle.</p>
 */
@Entity
@Table(
    name = "holdings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_holding_user_stock", columnNames = {"user_id", "stock_id"})
    },
    indexes = {
        @Index(name = "idx_holding_user", columnList = "user_id")
    }
)
@Check(constraints = "quantity >= 0 AND average_cost >= 0.000000 AND average_cost <= 9999999999999.999999 AND total_realized_pnl >= -9999999999999.99 AND total_realized_pnl <= 9999999999999.99")
public class Holding {

    public static final BigDecimal MIN_STOCK_PRICE = new BigDecimal("0.01");
    public static final BigDecimal MAX_STOCK_PRICE = new BigDecimal("9999999999.99");
    public static final BigDecimal MIN_MONETARY_VALUE = new BigDecimal("-9999999999999.99");
    public static final BigDecimal MAX_MONETARY_VALUE = new BigDecimal("9999999999999.99");
    public static final BigDecimal MIN_AVERAGE_COST = new BigDecimal("0.000000");
    public static final BigDecimal MAX_AVERAGE_COST = new BigDecimal("9999999999999.999999");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", referencedColumnName = "id", nullable = false)
    private Stock stock;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "average_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal averageCost;

    @Column(name = "total_realized_pnl", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRealizedPnl;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Holding() {}

    public Holding(User user, Stock stock) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (stock == null) {
            throw new IllegalArgumentException("Stock cannot be null");
        }
        this.user = user;
        this.stock = stock;
        this.quantity = 0;
        this.averageCost = MIN_AVERAGE_COST;
        this.totalRealizedPnl = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Holding(User user, Stock stock, Integer quantity, BigDecimal averageCost, BigDecimal totalRealizedPnl) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }
        if (stock == null) {
            throw new IllegalArgumentException("Stock cannot be null");
        }
        if (quantity == null || quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative or null");
        }
        if (averageCost == null) {
            throw new IllegalArgumentException("Average cost cannot be null");
        }
        BigDecimal normalizedAvg = averageCost.setScale(6, RoundingMode.HALF_UP);
        if (normalizedAvg.compareTo(MIN_AVERAGE_COST) < 0 || normalizedAvg.compareTo(MAX_AVERAGE_COST) > 0) {
            throw new IllegalArgumentException("Average cost out of supported bounds");
        }
        this.user = user;
        this.stock = stock;
        this.quantity = quantity;
        this.averageCost = normalizedAvg;
        this.totalRealizedPnl = (totalRealizedPnl != null)
            ? totalRealizedPnl.setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public record BuyCalculation(int newQuantity, BigDecimal newAverageCost) {}
    public record SellCalculation(BigDecimal orderRealizedPnl, BigDecimal newCumulativeRealizedPnl, int remainingQuantity) {}

    /**
     * Pre-computes and validates the resulting holding state for a BUY operation
     * without modifying any entity fields.
     */
    public BuyCalculation previewBuy(Integer buyQty, BigDecimal buyPrice) {
        // 1. Parameter presence validation
        if (buyQty == null || buyQty <= 0) {
            throw new IllegalArgumentException("Buy quantity must be strictly positive");
        }
        if (buyPrice == null) {
            throw new IllegalArgumentException("Buy price cannot be null");
        }

        // 2. Scale normalization and price boundary checks
        BigDecimal normalizedBuyPrice = buyPrice.setScale(2, RoundingMode.HALF_UP);
        if (normalizedBuyPrice.compareTo(MIN_STOCK_PRICE) < 0) {
            throw new IllegalArgumentException("Buy price must be strictly positive after normalization");
        }
        if (normalizedBuyPrice.compareTo(MAX_STOCK_PRICE) > 0) {
            throw new IllegalArgumentException("Buy price exceeds maximum permitted stock price (9,999,999,999.99)");
        }

        // 3. Integer overflow protection (propagates ArithmeticException directly if overflow occurs)
        int newTotalQty = Math.addExact(this.quantity, buyQty);

        // 4. Compute new WAC into local variable
        BigDecimal newAverageCost;
        if (this.quantity == 0) {
            // Reopening position: reset average cost directly to normalized buy price
            newAverageCost = normalizedBuyPrice.setScale(6, RoundingMode.HALF_UP);
        } else {
            BigDecimal currentTotalCost = BigDecimal.valueOf(this.quantity).multiply(this.averageCost);
            BigDecimal additionalCost = BigDecimal.valueOf(buyQty).multiply(normalizedBuyPrice);
            newAverageCost = currentTotalCost.add(additionalCost)
                .divide(BigDecimal.valueOf(newTotalQty), 6, RoundingMode.HALF_UP);
        }

        // 5. Validate calculated average cost against column capacity
        if (newAverageCost.compareTo(MAX_AVERAGE_COST) > 0) {
            throw new IllegalArgumentException("Calculated average cost exceeds database storage capacity");
        }

        return new BuyCalculation(newTotalQty, newAverageCost);
    }

    /**
     * Applies a pre-validated calculation result to the entity.
     */
    public void applyBuy(BuyCalculation calculation) {
        if (calculation == null) {
            throw new IllegalArgumentException("Buy calculation cannot be null");
        }
        this.quantity = calculation.newQuantity();
        this.averageCost = calculation.newAverageCost();
        this.updatedAt = Instant.now();
    }

    /**
     * Records a stock purchase, updating holding quantity and calculating weighted-average cost.
     *
     * @param buyQty   positive number of shares purchased
     * @param buyPrice execution price per share
     * @throws ArithmeticException if integer addition of quantity overflows Integer.MAX_VALUE
     * @throws IllegalArgumentException if parameters are invalid or bounds are breached
     */
    public void recordBuy(Integer buyQty, BigDecimal buyPrice) {
        applyBuy(previewBuy(buyQty, buyPrice));
    }

    /**
     * Pre-computes and validates the resulting holding state and realized P&L for a SELL operation
     * without modifying any entity fields.
     */
    public SellCalculation previewSell(Integer sellQty, BigDecimal sellPrice) {
        // 1. Parameter presence validation
        if (sellQty == null || sellQty <= 0) {
            throw new IllegalArgumentException("Sell quantity must be strictly positive");
        }
        if (sellPrice == null) {
            throw new IllegalArgumentException("Sell price cannot be null");
        }

        // 2. Scale normalization and price boundary checks (enforcing same ceiling as BUY)
        BigDecimal normalizedSellPrice = sellPrice.setScale(2, RoundingMode.HALF_UP);
        if (normalizedSellPrice.compareTo(MIN_STOCK_PRICE) < 0) {
            throw new IllegalArgumentException("Sell price must be strictly positive after normalization");
        }
        if (normalizedSellPrice.compareTo(MAX_STOCK_PRICE) > 0) {
            throw new IllegalArgumentException("Sell price exceeds maximum permitted stock price (9,999,999,999.99)");
        }

        // 3. Holding quantity availability validation
        if (sellQty > this.quantity) {
            throw new InsufficientHoldingException(
                "Insufficient shares: requested " + sellQty + ", currently holding " + this.quantity
            );
        }

        // 4. Compute per-order realized P&L into local variable
        BigDecimal priceDifference = normalizedSellPrice.subtract(this.averageCost);
        BigDecimal orderRealizedPnl = BigDecimal.valueOf(sellQty).multiply(priceDifference)
            .setScale(2, RoundingMode.HALF_UP);

        // Validate per-order P&L boundaries
        if (orderRealizedPnl.compareTo(MAX_MONETARY_VALUE) > 0 ||
            orderRealizedPnl.compareTo(MIN_MONETARY_VALUE) < 0) {
            throw new IllegalArgumentException("Order realized P&L exceeds supported monetary storage range");
        }

        // 5. Compute cumulative realized P&L into local variable
        BigDecimal newCumulativeRealizedPnl = this.totalRealizedPnl.add(orderRealizedPnl)
            .setScale(2, RoundingMode.HALF_UP);

        // Validate cumulative P&L boundaries
        if (newCumulativeRealizedPnl.compareTo(MAX_MONETARY_VALUE) > 0 ||
            newCumulativeRealizedPnl.compareTo(MIN_MONETARY_VALUE) < 0) {
            throw new IllegalArgumentException("Cumulative realized P&L exceeds supported monetary storage range");
        }

        return new SellCalculation(orderRealizedPnl, newCumulativeRealizedPnl, this.quantity - sellQty);
    }

    /**
     * Applies a pre-validated calculation result to the entity.
     */
    public void applySell(SellCalculation calculation) {
        if (calculation == null) {
            throw new IllegalArgumentException("Sell calculation cannot be null");
        }
        this.quantity = calculation.remainingQuantity();
        this.totalRealizedPnl = calculation.newCumulativeRealizedPnl();
        this.updatedAt = Instant.now();
    }

    /**
     * Records a stock sale, updating holding quantity and accumulating realized P&L.
     *
     * @param sellQty   positive number of shares sold (must be <= current quantity)
     * @param sellPrice execution price per share
     * @return the per-order realized profit or loss (positive for gain, negative for loss)
     * @throws IllegalArgumentException if parameters are invalid, insufficient shares, or bounds breached
     */
    public BigDecimal recordSell(Integer sellQty, BigDecimal sellPrice) {
        SellCalculation calc = previewSell(sellQty, sellPrice);
        applySell(calc);
        return calc.orderRealizedPnl();
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Stock getStock() { return stock; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getAverageCost() { return averageCost; }
    public BigDecimal getTotalRealizedPnl() { return totalRealizedPnl; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
