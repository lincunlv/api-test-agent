package com.apitestagent.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ObservabilityAnalysisMiddleware implements AnalysisMiddleware {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAnalysisMiddleware.class);

    @Override
    public AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain) {
        long start = System.currentTimeMillis();
        AnalysisExecutionResult result = chain.proceed(context);
        long duration = System.currentTimeMillis() - start;
        result.getMetadata().put("durationMs", duration);
        result.getMetadata().put("attempt", context.getAttempt());
        result.getMetadata().put("contextTrimmed", context.getAttributes().get("contextTrimmed"));
        result.getMetadata().put("contextLength", context.getAttributes().get("contextLength"));
        log.info("analysis_completed taskId={}, skill={}, attempt={}, durationMs={}, contextTrimmed={}",
            context.getTaskId(),
            context.getSkillType().getCode(),
            context.getAttempt(),
            duration,
            context.getAttributes().get("contextTrimmed"));
        return result;
    }
}
