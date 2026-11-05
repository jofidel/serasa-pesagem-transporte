package org.acme.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Business pricing rule: margin falls linearly from 20% to 5% as stock rises to 1,000 tonnes. */
public final class PricingPolicy {
    public static final BigDecimal MIN_MARGIN = new BigDecimal("0.05");
    public static final BigDecimal MAX_MARGIN = new BigDecimal("0.20");
    public static final BigDecimal REFERENCE_STOCK_KG = new BigDecimal("1000000.000");

    private PricingPolicy() {
    }

    public static BigDecimal marginForAvailableStock(BigDecimal availableKg) {
        BigDecimal normalizedStock = availableKg == null
                ? BigDecimal.ZERO
                : availableKg.max(BigDecimal.ZERO).min(REFERENCE_STOCK_KG);
        BigDecimal discount = MAX_MARGIN.subtract(MIN_MARGIN)
                .multiply(normalizedStock)
                .divide(REFERENCE_STOCK_KG, 8, RoundingMode.HALF_UP);
        return MAX_MARGIN.subtract(discount).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal salePrice(BigDecimal cargoCost, BigDecimal margin) {
        return cargoCost.multiply(BigDecimal.ONE.add(margin)).setScale(2, RoundingMode.HALF_UP);
    }
}
