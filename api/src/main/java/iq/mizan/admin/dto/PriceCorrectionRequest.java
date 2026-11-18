package iq.mizan.admin.dto;

import iq.mizan.market.entity.Instrument;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PriceCorrectionRequest(@NotNull Instrument instrument, @Positive long priceMicros, @NotBlank @Size(max = 300) String reason) {
}
