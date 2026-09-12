package iq.mizan.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class GoalProjectionVectorTest {

    private record Input(long targetAmount, long saved, Long monthlyContribution, String targetDate, String today) {
    }

    private record Expected(long remaining, double percent, Integer monthsToTarget, String projectedDate, Long requiredMonthly) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("goal-projection.json"), Input.class, new TypeReference<Expected>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> {
                    Input in = vector.input();
                    GoalProjection.Result result = GoalProjection.compute(in.targetAmount(), in.saved(), in.monthlyContribution(),
                            parse(in.targetDate()), LocalDate.parse(in.today()));
                    Expected e = vector.expected();
                    assertThat(result).isEqualTo(new GoalProjection.Result(
                            e.remaining(), e.percent(), e.monthsToTarget(), parse(e.projectedDate()), e.requiredMonthly()));
                }));
    }

    private static LocalDate parse(String date) {
        return date == null ? null : LocalDate.parse(date);
    }
}
