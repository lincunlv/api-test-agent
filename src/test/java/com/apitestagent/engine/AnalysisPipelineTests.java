package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisPipelineTests {

    @Test
    void shouldRetryAndEventuallySucceed() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setRetryMaxAttempts(2);
        properties.setRetryBackoffMs(0L);

        AtomicInteger invocationCount = new AtomicInteger(0);
        AnalysisRenderer flakyRenderer = (request, skillType, skillBundle, taskId) -> {
            int current = invocationCount.incrementAndGet();
            if (current == 1) {
                throw new IllegalStateException("temporary failure");
            }
            return "retry-ok";
        };

        TemplateAnalysisEngine engine = new TemplateAnalysisEngine(
            Collections.<AnalysisMiddleware>singletonList(new RetryAnalysisMiddleware(properties)),
            flakyRenderer
        );

        AnalysisExecutionResult result = engine.generate(sampleRequest("正常请求"), SkillType.A1, sampleSkillBundle(), "task-retry");
        assertEquals("retry-ok", result.getContent());
        assertEquals(2, invocationCount.get());
    }

    @Test
    void shouldExposeContextTrimmedMetadataWhenPromptTooLong() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setMaxContextCharacters(20);

        AnalysisRenderer renderer = (request, skillType, skillBundle, taskId) -> "content";

        TemplateAnalysisEngine engine = new TemplateAnalysisEngine(
            Arrays.<AnalysisMiddleware>asList(
                new ContextEditingAnalysisMiddleware(properties),
                new ObservabilityAnalysisMiddleware()
            ),
            renderer
        );

        AnalysisExecutionResult result = engine.generate(sampleRequest(repeat("超长上下文", 10)), SkillType.A2, sampleSkillBundle(), "task-context");
        assertEquals("content", result.getContent());
        assertEquals(Boolean.TRUE, result.getMetadata().get("contextTrimmed"));
        assertTrue(((Number) result.getMetadata().get("contextLength")).intValue() > 20);
    }

    @Test
    void shouldRejectWhenPromptExceedsGuardrailLimit() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setMaxPromptLength(5);

        TemplateAnalysisEngine engine = new TemplateAnalysisEngine(
            Collections.<AnalysisMiddleware>singletonList(new GuardrailAnalysisMiddleware(properties)),
            (request, skillType, skillBundle, taskId) -> "should-not-render"
        );

        CreateTaskRequest request = sampleRequest("超过限制的提示词");
        SkillBundle skillBundle = sampleSkillBundle();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> engine.generate(request, SkillType.A1, skillBundle, "task-guardrail"));
        assertTrue(exception.getMessage().contains("prompt 超过长度限制"));
    }

    @Test
    void shouldWrapUnexpectedRuntimeErrorWithTaskContext() {
        TemplateAnalysisEngine engine = new TemplateAnalysisEngine(
            Collections.<AnalysisMiddleware>singletonList(new ErrorBoundaryAnalysisMiddleware()),
            (request, skillType, skillBundle, taskId) -> {
                throw new RuntimeException("renderer exploded");
            }
        );

        CreateTaskRequest request = sampleRequest("正常请求");
        SkillBundle skillBundle = sampleSkillBundle();
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> engine.generate(request, SkillType.A2, skillBundle, "task-error-boundary"));
        assertTrue(exception.getMessage().contains("task-error-boundary"));
        assertTrue(exception.getMessage().contains("renderer exploded"));
    }

    @Test
    void shouldDelegateToLlmAdapterWhenRendererModeIsLlm() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setRendererMode("llm");
        properties.setLlmModel("mock-llm-model");

        AtomicInteger adapterInvocationCount = new AtomicInteger(0);
        LlmAnalysisAdapter llmAnalysisAdapter = request -> {
            adapterInvocationCount.incrementAndGet();
            assertEquals("mock-llm-model", request.getModel());
            assertEquals("task-llm", request.getTaskId());
            assertEquals("正常请求", request.getPrompt());
            return "llm-rendered-content";
        };

        ConfigurableAnalysisRenderer renderer = new ConfigurableAnalysisRenderer(
            properties,
            new TemplateAnalysisRenderer(),
            llmAnalysisAdapter
        );

        String content = renderer.render(sampleRequest("正常请求"), SkillType.A3, sampleSkillBundle(), "task-llm");
        assertEquals("llm-rendered-content", content);
        assertEquals(1, adapterInvocationCount.get());
    }

    @Test
    void shouldThrowClearErrorWhenLlmAdapterIsNotConfigured() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setRendererMode("llm");

        ConfigurableAnalysisRenderer renderer = new ConfigurableAnalysisRenderer(
            properties,
            new TemplateAnalysisRenderer(),
            new NoopLlmAnalysisAdapter()
        );

        CreateTaskRequest request = sampleRequest("正常请求");
        SkillBundle skillBundle = sampleSkillBundle();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> renderer.render(request, SkillType.A1, skillBundle, "task-llm-fail"));
        assertTrue(exception.getMessage().contains("LLM renderer is not configured"));
    }

    private CreateTaskRequest sampleRequest(String prompt) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskType("A1");
        request.setPrompt(prompt);
        LinkedHashMap<String, Object> chainData = new LinkedHashMap<>();
        LinkedHashMap<String, Object> interfaceData = new LinkedHashMap<>();
        interfaceData.put("name", "demo");
        interfaceData.put("path", "/demo");
        interfaceData.put("method", "POST");
        chainData.put("interface", interfaceData);
        request.setChainData(chainData);
        return request;
    }

    private SkillBundle sampleSkillBundle() {
        return new SkillBundle("skill-index", "reference-content");
    }

    private String repeat(String value, int times) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < times; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}