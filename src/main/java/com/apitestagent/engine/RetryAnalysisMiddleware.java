package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(40)
public class RetryAnalysisMiddleware implements AnalysisMiddleware {

    private final AnalysisProperties properties;

    public RetryAnalysisMiddleware(AnalysisProperties properties) {
        this.properties = properties;
    }

    @Override
    public AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain) {
        RuntimeException lastException = null;
        int maxAttempts = Math.max(1, properties.getRetryMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            context.setAttempt(attempt);
            try {
                return chain.proceed(context);
            } catch (RuntimeException ex) {
                lastException = ex;
                if (attempt == maxAttempts) {
                    throw ex;
                }
                sleepQuietly(properties.getRetryBackoffMs());
            }
        }
        throw lastException == null ? new IllegalStateException("分析执行失败") : lastException;
    }

    private void sleepQuietly(long backoffMs) {
        try {
            Thread.sleep(Math.max(0L, backoffMs));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("分析重试被中断", ex);
        }
    }
}
