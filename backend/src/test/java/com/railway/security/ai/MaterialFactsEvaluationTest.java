package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MaterialFactsEvaluationTest {
    @Test
    void syntheticOfflineSetHasExactFieldsAndMissingItemRecall() throws Exception {
        var json = new ObjectMapper();
        var fixture = Path.of("..", "scripts", "ai", "fixtures", "material-facts.jsonl");
        var lines = Files.readAllLines(fixture);
        int cases = 0;
        for (String line : lines) {
            if (line.isBlank()) continue;
            var sample = json.readTree(line);
            var actual =
                    MaterialFacts.inspect(
                            sample.get("text").asText(),
                            sample.get("uploadedType").asText(),
                            sample.get("expectedUnit").asText());
            var expected = sample.get("expected");
            assertEquals(
                    expected.get("category").asText(),
                    actual.category(),
                    sample.get("id").asText());
            assertEquals(
                    expected.get("date").isNull() ? null : expected.get("date").asText(),
                    actual.date(),
                    sample.get("id").asText());
            assertEquals(
                    expected.get("unit").isNull() ? null : expected.get("unit").asText(),
                    actual.unit(),
                    sample.get("id").asText());
            assertEquals(
                    json.convertValue(
                            expected.get("missingItems"),
                            new com.fasterxml.jackson.core.type.TypeReference<
                                    java.util.List<String>>() {}),
                    actual.missingItems(),
                    sample.get("id").asText());
            cases++;
        }
        assertEquals(8, cases);
    }
}
