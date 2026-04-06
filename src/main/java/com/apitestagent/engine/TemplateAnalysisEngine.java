package com.apitestagent.engine;

import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TemplateAnalysisEngine implements AnalysisEngine {

    private final List<AnalysisMiddleware> middlewares;

    private final AnalysisRenderer renderer;

    public TemplateAnalysisEngine(List<AnalysisMiddleware> middlewares, AnalysisRenderer renderer) {
        this.middlewares = middlewares;
        this.renderer = renderer;
    }

    @Override
    public AnalysisExecutionResult generate(CreateTaskRequest request, SkillType skillType, SkillBundle skillBundle, String taskId) {
        AnalysisExecutionContext context = new AnalysisExecutionContext(taskId, request, skillType, skillBundle);
        DefaultAnalysisExecutionChain chain = new DefaultAnalysisExecutionChain(middlewares, renderer);
        return chain.proceed(context);
    }
}
