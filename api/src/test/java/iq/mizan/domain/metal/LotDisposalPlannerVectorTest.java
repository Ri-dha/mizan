package iq.mizan.domain.metal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import iq.mizan.support.TestVectors;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class LotDisposalPlannerVectorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Lot(String id, String purchaseDate, long weightMg, long remainingMg, long metalCost, long makingCharge, long fees) {
        LotDisposalPlanner.Lot toDomain() {
            return new LotDisposalPlanner.Lot(id, LocalDate.parse(purchaseDate), weightMg, remainingMg, metalCost, makingCharge, fees);
        }
    }

    private record Input(List<Lot> lots, long weightMg, LotDisposalPlanner.Method method, List<String> specificLotIds, long proceeds) {
    }

    @TestFactory
    Stream<DynamicTest> sharedVectors() {
        return TestVectors.load(Path.of("lot-disposal.json"), Input.class, new TypeReference<JsonNode>() { })
                .map(vector -> DynamicTest.dynamicTest(vector.name(), () -> {
                    Input in = vector.input();
                    List<LotDisposalPlanner.Lot> lots = in.lots().stream().map(Lot::toDomain).toList();
                    if (vector.expected().has("error")) {
                        assertThatThrownBy(() -> LotDisposalPlanner.plan(lots, in.weightMg(), in.method(), in.specificLotIds(), in.proceeds()))
                                .isInstanceOf(LotDisposalPlanner.InsufficientWeightException.class);
                        return;
                    }
                    LotDisposalPlanner.Plan plan = LotDisposalPlanner.plan(lots, in.weightMg(), in.method(), in.specificLotIds(), in.proceeds());
                    assertThat(plan).isEqualTo(JSON.treeToValue(vector.expected(), LotDisposalPlanner.Plan.class));
                }));
    }
}
