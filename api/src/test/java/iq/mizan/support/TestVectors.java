package iq.mizan.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Loads a case file from {@code shared/test-vectors}, the contract shared with the web client. */
public final class TestVectors {

    private static final Path DIRECTORY = Path.of("..", "shared", "test-vectors");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TestVectors() {
    }

    public record Vector<I, E>(String name, I input, E expected) {
    }

    public static <I, E> Stream<Vector<I, E>> load(Path file, Class<I> inputType, TypeReference<E> expectedType) {
        try {
            List<JsonNode> cases = JSON.readValue(Files.readString(DIRECTORY.resolve(file)), new TypeReference<>() { });
            return cases.stream().map(node -> new Vector<>(
                    node.get("name").asString(),
                    JSON.treeToValue(node.get("input"), inputType),
                    JSON.treeToValue(node.get("expected"), expectedType)));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read test vectors " + file, e);
        }
    }
}
