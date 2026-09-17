package dev.qiqi.dataagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties("qiqi")
public record QiqiProperties(Model model, Query query, Set<String> exposedTables) {
    public QiqiProperties {
        model = model == null ? new Model("", "qwen-plus", 12) : model;
        query = query == null ? new Query(200, Duration.ofSeconds(10)) : query;
        exposedTables = exposedTables == null || exposedTables.isEmpty()
                ? new LinkedHashSet<>(Set.of("department", "customer", "product", "sales_order", "sales_order_item"))
                : exposedTables.stream().map(String::toLowerCase).collect(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public record Model(String apiKey, String name, int maxIterations) {
        public Model {
            apiKey = apiKey == null ? "" : apiKey.trim();
            name = name == null || name.isBlank() ? "qwen-plus" : name.trim();
            maxIterations = maxIterations < 1 ? 12 : maxIterations;
        }
    }

    public record Query(int maxRows, Duration timeout) {
        public Query {
            maxRows = maxRows < 1 ? 200 : maxRows;
            timeout = timeout == null || timeout.isNegative() || timeout.isZero()
                    ? Duration.ofSeconds(10) : timeout;
        }
    }
}
