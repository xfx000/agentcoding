package dev.qiqi.dataagent.query;

import java.util.List;
import java.util.Map;

public record QueryResult(String queryId, List<String> columns, List<Map<String, Object>> rows,
                          int rowCount, boolean truncated, long durationMs, String executedSql) {}
