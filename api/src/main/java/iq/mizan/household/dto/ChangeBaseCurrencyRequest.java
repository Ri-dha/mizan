package iq.mizan.household.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** {@code factor}: new currency units per old one at the rate the owner's device used. */
public record ChangeBaseCurrencyRequest(
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal factor) {
}
