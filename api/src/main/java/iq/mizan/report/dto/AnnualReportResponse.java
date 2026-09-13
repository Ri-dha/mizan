package iq.mizan.report.dto;

import java.util.List;

/** FR-RPT-03 over shared records only: what an advisor may see (§4.2 P4). Amounts in base minor units. */
public record AnnualReportResponse(int year, String baseCurrency, List<Month> months, long received, long spent, Double savingRate,
                                   List<Snapshot> snapshots) {

    public record Month(String monthKey, long received, long spent, long saved, Double savingRate) {
    }

    public record Snapshot(String monthKey, long netWorth) {
    }
}
