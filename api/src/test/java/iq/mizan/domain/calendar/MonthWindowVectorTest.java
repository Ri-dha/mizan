package iq.mizan.domain.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;

class MonthWindowVectorTest {

    private record Input(String key, String date, int startDay) {
    }

    private record Expected(String from, String toExclusive, String key) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("month-window.json"), Input.class, new TypeReference<Expected>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> {
                    if (vector.input().key() != null) {
                        MonthWindow window = MonthWindow.of(vector.input().key(), vector.input().startDay());
                        assertThat(window.from()).isEqualTo(LocalDate.parse(vector.expected().from()));
                        assertThat(window.toExclusive()).isEqualTo(LocalDate.parse(vector.expected().toExclusive()));
                    } else {
                        assertThat(MonthWindow.keyFor(LocalDate.parse(vector.input().date()), vector.input().startDay()))
                                .isEqualTo(vector.expected().key());
                    }
                }));
    }
}
