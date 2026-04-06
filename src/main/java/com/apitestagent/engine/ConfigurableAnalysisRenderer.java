package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Primary
@Component
public class ConfigurableAnalysisRenderer implements AnalysisRenderer {

    private static final String LLM_MODE = "llm";
    private static final String MOCK_MODE = "mock-model";
    private static final String MOCK_FAILURE_MODE = "mock-failure";

    private final AnalysisProperties properties;

    private final TemplateAnalysisRenderer templateAnalysisRenderer;

    private final LlmAnalysisAdapter llmAnalysisAdapter;

    public ConfigurableAnalysisRenderer(AnalysisProperties properties,
                                        TemplateAnalysisRenderer templateAnalysisRenderer,
                                        LlmAnalysisAdapter llmAnalysisAdapter) {
        this.properties = properties;
        this.templateAnalysisRenderer = templateAnalysisRenderer;
        this.llmAnalysisAdapter = llmAnalysisAdapter;
    }

    @Override
    public String render(CreateTaskRequest request, SkillType skillType, SkillBundle skillBundle, String taskId) {
        String requestedMode = resolveRequestedMode(request.getOptions());
        if (LLM_MODE.equalsIgnoreCase(requestedMode)) {
            return llmAnalysisAdapter.render(buildLlmRenderRequest(request, skillType, skillBundle, taskId));
        }
        if (MOCK_FAILURE_MODE.equalsIgnoreCase(requestedMode)) {
            throw new IllegalStateException(resolveFailureMessage(request.getOptions()));
        }
        if (MOCK_MODE.equalsIgnoreCase(requestedMode)) {
            String mockOutput = resolveMockOutput(request.getOptions());
            if (StringUtils.hasText(mockOutput)) {
                return mockOutput;
            }
        }
        return templateAnalysisRenderer.render(request, skillType, skillBundle, taskId);
    }

    private String resolveRequestedMode(Map<String, Object> options) {
        if (options != null && options.get("rendererMode") != null) {
            return String.valueOf(options.get("rendererMode"));
        }
        return properties.getRendererMode();
    }

    private String resolveMockOutput(Map<String, Object> options) {
        if (options == null || options.get("mockOutput") == null) {
            return null;
        }
        return String.valueOf(options.get("mockOutput"));
    }

    private String resolveFailureMessage(Map<String, Object> options) {
        if (options == null || options.get("mockFailureMessage") == null) {
            return "mock renderer failure";
        }
        return String.valueOf(options.get("mockFailureMessage"));
    }

    private LlmRenderRequest buildLlmRenderRequest(CreateTaskRequest request,
                                                   SkillType skillType,
                                                   SkillBundle skillBundle,
                                                   String taskId) {
        LlmRenderRequest llmRenderRequest = new LlmRenderRequest();
        llmRenderRequest.setTaskId(taskId);
        llmRenderRequest.setSkillType(skillType);
        llmRenderRequest.setModel(properties.getLlmModel());
        llmRenderRequest.setPrompt(request.getPrompt());
        llmRenderRequest.setSkillIndexContent(skillBundle.getSkillIndexContent());
        llmRenderRequest.setReferenceContent(skillBundle.getReferenceContent());
        llmRenderRequest.setChainData(request.getChainData());
        llmRenderRequest.setOptions(request.getOptions());
        return llmRenderRequest;
    }
}