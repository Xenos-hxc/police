package com.railway.security.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

class LocalQdrantVectorStoreTest {
    @Test
    void refusesMissingNumericAndNonDepartmentFiltersBeforeEmbedding() {
        var embedding = mock(EmbeddingModel.class);
        var store =
                new LocalQdrantVectorStore(embedding, "127.0.0.1", 6333, "test", true, false, "");
        for (var request :
                java.util.List.of(
                        SearchRequest.builder().query("x").build(),
                        SearchRequest.builder()
                                .query("x")
                                .filterExpression("deptId == 101")
                                .build(),
                        SearchRequest.builder()
                                .query("x")
                                .filterExpression("policyId == '101'")
                                .build(),
                        SearchRequest.builder()
                                .query("x")
                                .filterExpression("deptId != '101'")
                                .build())) {
            assertThrows(IllegalArgumentException.class, () -> store.similaritySearch(request));
        }
        verifyNoInteractions(embedding);
    }

    @Test
    void sendsDepartmentFilterAndDiscardsUnexpectedDepartmentInResponse() throws Exception {
        var embedding = mock(EmbeddingModel.class);
        when(embedding.embed("question")).thenReturn(new float[] {0.1f, 0.2f});
        var captured = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/collections/test/points/query",
                exchange -> {
                    captured.set(
                            new String(
                                    exchange.getRequestBody().readAllBytes(),
                                    StandardCharsets.UTF_8));
                    byte[] response =
                            """
                {"result":{"points":[
                  {"id":"81b206a8-9f89-35b1-923b-87606075c6a8","score":0.8,"payload":{"deptId":"101","policyId":"7","doc_content":"allowed"}},
                  {"id":"fec2e738-4c5c-3b1d-b525-1d11dddec075","score":0.9,"payload":{"deptId":"102","policyId":"8","doc_content":"forbidden"}}
                ]}}
                """
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            var store =
                    new LocalQdrantVectorStore(
                            embedding,
                            "127.0.0.1",
                            server.getAddress().getPort(),
                            "test",
                            true,
                            false,
                            "");
            var hits =
                    store.similaritySearch(
                            SearchRequest.builder()
                                    .query("question")
                                    .filterExpression("deptId == '101'")
                                    .build());
            assertEquals(1, hits.size());
            assertEquals("allowed", hits.get(0).getText());
            var request = new ObjectMapper().readTree(captured.get());
            assertEquals(
                    "101",
                    request.path("filter")
                            .path("must")
                            .get(0)
                            .path("match")
                            .path("value")
                            .asText());
        } finally {
            server.stop(0);
        }
    }
}
