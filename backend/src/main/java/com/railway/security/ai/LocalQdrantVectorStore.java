package com.railway.security.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Spring AI VectorStore using Qdrant's current REST query API. Spring AI 1.1's bundled gRPC client
 * uses an older, binary-incompatible protobuf generation. Only department equality filters are
 * supported, and an absent filter fails closed.
 */
@Component
public class LocalQdrantVectorStore implements VectorStore, InitializingBean {
    private final EmbeddingModel embedding;
    private final RestClient client;
    private final String collection;
    private final boolean enabled;
    private final boolean initialize;

    public LocalQdrantVectorStore(
            EmbeddingModel embedding,
            @Value("${spring.ai.vectorstore.qdrant.host:127.0.0.1}") String host,
            @Value("${app.ai.qdrant-http-port:6333}") int port,
            @Value("${spring.ai.vectorstore.qdrant.collection-name:railway_policy}")
                    String collection,
            @Value("${app.ai.enabled:false}") boolean enabled,
            @Value("${spring.ai.vectorstore.qdrant.initialize-schema:false}") boolean initialize,
            @Value("${app.ai.qdrant-api-key:}") String apiKey) {
        if (!collection.matches("[a-zA-Z0-9_-]{1,100}"))
            throw new IllegalArgumentException("Invalid collection name");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid Qdrant port");
        this.embedding = embedding;
        this.collection = collection;
        this.enabled = enabled;
        this.initialize = initialize;
        var http =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        String authority = host.contains(":") ? "[" + host + "]" : host;
        var builder =
                RestClient.builder()
                        .baseUrl(URI.create("http://" + authority + ":" + port).toString())
                        .requestFactory(factory);
        if (!apiKey.isBlank()) builder.defaultHeader("api-key", apiKey);
        client = builder.build();
    }

    @Override
    // 初始化验证向量维度并建立部门字段索引；余弦相似度与检索参数需结合实际语料评估，不能把默认配置说成调优成果。
    public void afterPropertiesSet() {
        if (!enabled) return;
        int dimensions = embedding.dimensions();
        JsonNode details;
        try {
            details = client.get().uri(path()).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404 || !initialize) throw exception;
            client.put()
                    .uri(path())
                    .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                    .retrieve()
                    .toBodilessEntity();
            details = client.get().uri(path()).retrieve().body(JsonNode.class);
        }
        if (details == null
                || details.path("result")
                                .path("config")
                                .path("params")
                                .path("vectors")
                                .path("size")
                                .asInt()
                        != dimensions) {
            throw new IllegalStateException(
                    "Vector dimensions differ from embedding model; use a new collection and reindex");
        }
        if (initialize) {
            client.put()
                    .uri(path() + "/index?wait=true")
                    .body(Map.of("field_name", "deptId", "field_schema", "keyword"))
                    .retrieve()
                    .toBodilessEntity();
        }
    }

    @Override
    public void add(List<Document> documents) {
        requireEnabled();
        var points = new ArrayList<Map<String, Object>>();
        for (Document document : documents) {
            UUID.fromString(document.getId());
            Object department = document.getMetadata().get("deptId");
            if (!(department instanceof String value) || !value.matches("[1-9][0-9]{0,17}")) {
                throw new IllegalArgumentException(
                        "Document department must be a canonical string ID");
            }
            var payload = new HashMap<>(document.getMetadata());
            payload.put("doc_content", document.getText());
            points.add(
                    Map.of(
                            "id",
                            document.getId(),
                            "vector",
                            embedding.embed(document.getText()),
                            "payload",
                            payload));
        }
        if (!points.isEmpty())
            client.put()
                    .uri(path() + "/points?wait=true")
                    .body(Map.of("points", points))
                    .retrieve()
                    .toBodilessEntity();
    }

    @Override
    public void delete(List<String> ids) {
        requireEnabled();
        ids.forEach(UUID::fromString);
        if (!ids.isEmpty())
            client.post()
                    .uri(path() + "/points/delete?wait=true")
                    .body(Map.of("points", ids))
                    .retrieve()
                    .toBodilessEntity();
    }

    @Override
    public void delete(Filter.Expression filter) {
        throw new UnsupportedOperationException("Delete only explicit deterministic chunk IDs");
    }

    // 部门 metadata 的写入类型与过滤类型必须一致；缺少或不支持的范围表达式拒绝检索，禁止退化为跨部门全库搜索。
    static String department(Filter.Expression expression) {
        if (expression == null
                || expression.type() != Filter.ExpressionType.EQ
                || !(expression.left() instanceof Filter.Key key)
                || !"deptId".equals(key.key())
                || !(expression.right() instanceof Filter.Value operand)
                || !(operand.value() instanceof String value)
                || !value.matches("[1-9][0-9]{0,17}")) {
            throw new IllegalArgumentException("Department equality filter is mandatory");
        }
        return value;
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        requireEnabled();
        String department = department(request.getFilterExpression());
        Map<String, Object> body =
                Map.of(
                        "query",
                        embedding.embed(request.getQuery()),
                        "limit",
                        Math.min(20, request.getTopK()),
                        "score_threshold",
                        request.getSimilarityThreshold(),
                        "with_payload",
                        true,
                        "filter",
                        Map.of(
                                "must",
                                List.of(
                                        Map.of(
                                                "key",
                                                "deptId",
                                                "match",
                                                Map.of("value", department)))));
        JsonNode response =
                client.post()
                        .uri(path() + "/points/query")
                        .body(body)
                        .retrieve()
                        .body(JsonNode.class);
        if (response == null || !response.path("result").path("points").isArray())
            throw new IllegalStateException("Invalid vector response");
        var documents = new ArrayList<Document>();
        for (JsonNode point : response.path("result").path("points")) {
            JsonNode payload = point.path("payload");
            // Second gate at the adapter boundary, before even returning the document.
            if (!department.equals(payload.path("deptId").asText())) continue;
            documents.add(
                    Document.builder()
                            .id(point.path("id").asText())
                            .text(payload.path("doc_content").asText())
                            .metadata(
                                    Map.of(
                                            "deptId",
                                            department,
                                            "policyId",
                                            payload.path("policyId").asText()))
                            .score(point.path("score").asDouble())
                            .build());
        }
        return documents;
    }

    private String path() {
        return "/collections/" + collection;
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("AI disabled");
    }
}
