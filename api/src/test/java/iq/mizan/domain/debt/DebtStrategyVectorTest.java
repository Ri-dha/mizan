package iq.mizan.domain.debt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class DebtStrategyVectorTest {

    private record Input(List<DebtStrategy.Debt> debts, long extraMonthly) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("debt-strategy.json"), Input.class, new TypeReference<DebtStrategy.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(DebtStrategy.compare(vector.input().debts(), vector.input().extraMonthly())).isEqualTo(vector.expected())));
    }
}
