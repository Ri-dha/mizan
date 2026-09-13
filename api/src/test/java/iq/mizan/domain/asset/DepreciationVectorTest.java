package iq.mizan.domain.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class DepreciationVectorTest {

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("asset-depreciation.json"), Depreciation.Input.class, new TypeReference<Depreciation.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(Depreciation.compute(vector.input())).isEqualTo(vector.expected())));
    }
}
