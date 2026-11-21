package iq.mizan.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorldRatePointResponse(LocalDate day, BigDecimal perUsd) {
}
