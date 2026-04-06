package com.apitestagent.engine;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnalysisExecutionResult {

    private final String content;
    private final Map<String, Object> metadata = new LinkedHashMap<>();

    public AnalysisExecutionResult(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
