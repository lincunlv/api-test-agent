package com.apitestagent.engine;

public class NoopLlmAnalysisAdapter implements LlmAnalysisAdapter {

    @Override
    public String render(LlmRenderRequest request) {
        throw new IllegalStateException("LLM renderer is not configured. Please provide an adapter implementation or use template/mock-model mode.");
    }
}