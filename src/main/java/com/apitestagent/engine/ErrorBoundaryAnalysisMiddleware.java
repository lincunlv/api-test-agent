package com.apitestagent.engine;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(50)
public class ErrorBoundaryAnalysisMiddleware implements AnalysisMiddleware {

    @Override
    public AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain) {
        try {
            return chain.proceed(context);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("分析执行失败, taskId=" + context.getTaskId() + ", reason=" + ex.getMessage(), ex);
        }
    }
}
