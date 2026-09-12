package iq.mizan.domain.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class MonthFiguresVectorTest {

    private record Input(long plannedIncome, long receivedIncome, List<MonthFigures.Bucket> buckets,
                         Map<String, Long> committed, Map<String, Long> spent,
                         Map<String, Long> transfersIn, Map<String, Long> transfersOut) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("month-figures.json"), Input.class, new TypeReference<MonthFigures.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> {
                    Input in = vector.input();
                    MonthFigures.Result result = MonthFigures.compute(new MonthFigures.Input(
                            in.plannedIncome(), in.receivedIncome(), in.buckets(), in.committed(), in.spent(),
                            in.transfersIn(), in.transfersOut()));
                    assertThat(result).isEqualTo(vector.expected());
                }));
    }
}
