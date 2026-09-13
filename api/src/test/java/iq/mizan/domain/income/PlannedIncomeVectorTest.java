package iq.mizan.domain.income;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import iq.mizan.domain.calendar.MonthWindow;
import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class PlannedIncomeVectorTest {

    private record Window(String from, String toExclusive) {
    }

    private record Step(String effectiveFrom, long baseAmount) {
    }

    private record Source(long baseAmount, Frequency frequency, Integer payDay, String anchorDate,
                          String activeFrom, String activeTo, List<Step> amountSteps) {

        PlannedIncome.Source toDomain() {
            return new PlannedIncome.Source(baseAmount, frequency, payDay, parse(anchorDate), parse(activeFrom), parse(activeTo),
                    amountSteps.stream().map(step -> new PlannedIncome.AmountStep(parse(step.effectiveFrom()), step.baseAmount())).toList());
        }
    }

    private record Input(Window window, List<Source> sources) {
    }

    private record Occurrence(int sourceIndex, String date, long amount) {
    }

    private record Expected(long total, List<Occurrence> occurrences) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("planned-income.json"), Input.class, new TypeReference<Expected>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> {
                    MonthWindow window = new MonthWindow("vector",
                            parse(vector.input().window().from()), parse(vector.input().window().toExclusive()));
                    PlannedIncome.Result result = PlannedIncome.compute(
                            vector.input().sources().stream().map(Source::toDomain).toList(), window);

                    assertThat(result.total()).isEqualTo(vector.expected().total());
                    assertThat(result.occurrences()).containsExactlyElementsOf(vector.expected().occurrences().stream()
                            .map(o -> new PlannedIncome.Occurrence(o.sourceIndex(), parse(o.date()), o.amount()))
                            .toList());
                }));
    }

    private static LocalDate parse(String date) {
        return date == null ? null : LocalDate.parse(date);
    }
}
