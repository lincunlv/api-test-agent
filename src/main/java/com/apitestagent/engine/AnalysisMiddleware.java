package com.apitestagent.engine;

public interface AnalysisMiddleware {

    AnalysisExecutionResult invoke(AnalysisExecutionContext context, AnalysisExecutionChain chain);
}
