package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(10)
public class GuardrailAnalysisMiddleware implements AnalysisMiddleware {

    private final AnalysisProperties properties;

    public GuardrailAnalysisMiddleware(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain) {
        if (!StringUtils.hasText(context.getRequest().getPrompt())) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (context.getRequest().getPrompt().length() > properties.getMaxPromptLength()) {
            throw new IllegalArgumentException("prompt 超过长度限制: " + properties.getMaxPromptLength());
        }
        if (context.getRequest().getChainData() == null || context.getRequest().getChainData().isEmpty()) {
            throw new IllegalArgumentException("chainData 不能为空");
        }
        if (!StringUtils.hasText(context.getSkillBundle().getSkillIndexContent())
            || !StringUtils.hasText(context.getSkillBundle().getReferenceContent())) {
            throw new IllegalArgumentException("Skill 模板内容不能为空");
        }
        return chain.proceed(context);
    }
}
