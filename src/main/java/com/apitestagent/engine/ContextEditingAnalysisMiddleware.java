package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ContextEditingAnalysisMiddleware implements AnalysisMiddleware {

    private final AnalysisProperties properties;

    public ContextEditingAnalysisMiddleware(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain) {
        int totalContextLength = safeLength(context.getRequest().getPrompt())
            + safeLength(context.getSkillBundle().getSkillIndexContent())
            + safeLength(context.getSkillBundle().getReferenceContent());
        context.getAttributes().put("contextLength", totalContextLength);
        if (totalContextLength > properties.getMaxContextCharacters()) {
            context.getAttributes().put("contextTrimmed", Boolean.TRUE);
            context.getAttributes().put("contextTrimmedCharacters", totalContextLength - properties.getMaxContextCharacters());
        } else {
            context.getAttributes().put("contextTrimmed", Boolean.FALSE);
        }
        return chain.proceed(context);
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }
}
