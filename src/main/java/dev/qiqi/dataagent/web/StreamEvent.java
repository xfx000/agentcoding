package dev.qiqi.dataagent.web;

import java.util.Map;

public record StreamEvent(String type, Map<String, Object> data) {
    public static StreamEvent error(String message) {
        return new StreamEvent("ERROR", Map.of("message", message));
    }
}
