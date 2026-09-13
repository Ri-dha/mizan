package iq.mizan.domain.expense;

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

class ExpenseScheduleVectorTest {

    private record Input(ExpenseSchedule.Schedule schedule, String monthKey, int startDay) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("expense-schedule.json"), Input.class, new TypeReference<List<LocalDate>>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () ->
                        assertThat(ExpenseSchedule.dueDates(vector.input().schedule(), MonthWindow.of(vector.input().monthKey(), vector.input().startDay())))
                                .isEqualTo(vector.expected())));
    }
}
