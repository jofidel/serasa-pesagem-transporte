package org.acme.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PricingPolicyTest {
    @Test
    void marginIsAtMaximumWhenStockIsEmptyOrUnknown() {
        assertEquals(new BigDecimal("0.2000"), PricingPolicy.marginForAvailableStock(BigDecimal.ZERO));
        assertEquals(new BigDecimal("0.2000"), PricingPolicy.marginForAvailableStock(null));
    }

    @Test
    void marginFallsAsAvailableStockIncreasesAndNeverLeavesTheRange() {
        BigDecimal scarce = PricingPolicy.marginForAvailableStock(new BigDecimal("100000"));
        BigDecimal abundant = PricingPolicy.marginForAvailableStock(new BigDecimal("800000"));
        assertEquals(new BigDecimal("0.1850"), scarce);
        assertEquals(new BigDecimal("0.0800"), abundant);
        assertEquals(new BigDecimal("0.0500"),
                PricingPolicy.marginForAvailableStock(new BigDecimal("2000000")));
    }

    @Test
    void estimatedSalePriceUsesTheSavedMargin() {
        assertEquals(new BigDecimal("1200.00"),
                PricingPolicy.salePrice(new BigDecimal("1000.00"), new BigDecimal("0.20")));
    }
}
