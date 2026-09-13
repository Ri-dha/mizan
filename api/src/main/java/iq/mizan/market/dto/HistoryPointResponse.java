package iq.mizan.market.dto;

import java.time.LocalDate;

public record HistoryPointResponse(LocalDate day, long priceMicros, String source) {
}
