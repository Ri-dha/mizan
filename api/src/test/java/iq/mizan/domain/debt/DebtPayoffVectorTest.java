package iq.mizan.domain.debt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class DebtPayoffVectorTest {

    private record Input(long balance, int annualRateBasisPoints, long monthlyPayment) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("debt-payoff.json"), Input.class, new TypeReference<DebtPayoff.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> assertThat(DebtPayoff.compute(
                        vector.input().balance(), vector.input().annualRateBasisPoints(), vector.input().monthlyPayment()))
                        .isEqualTo(vector.expected())));
    }
}
