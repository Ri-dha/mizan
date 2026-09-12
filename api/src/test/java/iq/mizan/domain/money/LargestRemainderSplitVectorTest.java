package iq.mizan.domain.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/** The same cases the web client runs; both must agree to the dinar. */
class LargestRemainderSplitVectorTest {

    private record Input(long total, List<Integer> sharesBasisPoints) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("money-split.json"), Input.class, new tools.jackson.core.type.TypeReference<List<Long>>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(LargestRemainderSplit.split(vector.input().total(), vector.input().sharesBasisPoints()))
                                .isEqualTo(vector.expected())));
    }
}
