package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class RagCitationEvaluationTest {
    @Test
    void syntheticGroundingGateSetRejectsMissingAndInventedReferences() throws Exception {
        var json = new ObjectMapper();
        var fixture = Path.of("..", "scripts", "ai", "fixtures", "rag-citation-gate.jsonl");
        int cases = 0;
        for (String line : Files.readAllLines(fixture)) {
            if (line.isBlank()) continue;
            var sample = json.readTree(line);
            var citations = new ArrayList<AiAssistanceService.Citation>();
            for (int index = 1; index <= sample.get("citationCount").asInt(); index++) {
                citations.add(
                        new AiAssistanceService.Citation(
                                index, index, "合成规章", "测试版", "合成出处", "示例片段"));
            }
            var answer = AiAssistanceService.verifyCitations(sample.get("raw").asText(), citations);
            assertEquals(
                    sample.get("expectedInsufficientEvidence").asBoolean(),
                    answer.insufficientEvidence(),
                    sample.get("id").asText());
            cases++;
        }
        assertEquals(6, cases);
    }
}
