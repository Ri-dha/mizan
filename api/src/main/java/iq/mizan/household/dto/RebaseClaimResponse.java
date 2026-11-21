package iq.mizan.household.dto;

import java.math.BigDecimal;

/**
 * {@code claimed}: this device converts the caller's private records, multiplying each base
 * figure by {@code factor} (all changes since the last claim, in whole units of the currencies),
 * from {@code fromCurrency}, the base those records were last converted to.
 */
public record RebaseClaimResponse(boolean claimed, int generation, String fromCurrency, String baseCurrency, BigDecimal factor) {
}
