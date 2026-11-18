package iq.mizan.market.dto;

import java.time.LocalDate;

/** {@code day} is the period's first day; {@code priceMicros} is its close. */
public record HistoryPointResponse(LocalDate day, long openMicros, long highMicros, long lowMicros, long priceMicros, String source) {
}
