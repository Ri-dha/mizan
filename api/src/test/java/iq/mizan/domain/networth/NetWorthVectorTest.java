package iq.mizan.domain.networth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class NetWorthVectorTest {

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("net-worth.json"), NetWorth.Input.class, new TypeReference<NetWorth.Result>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(NetWorth.compute(vector.input())).isEqualTo(vector.expected())));
    }
}
