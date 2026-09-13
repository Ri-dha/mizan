package iq.mizan.domain.metal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class MetalValuationVectorTest {

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("metal-valuation.json"), MetalValuation.Input.class, new TypeReference<MetalValuation.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(MetalValuation.compute(vector.input())).isEqualTo(vector.expected())));
    }
}
