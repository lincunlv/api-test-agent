package com.apitestagent.engine;

public interface AnalysisExecutionChain {

    AnalysisExecutionResult proceed(AnalysisExecutionContext context);
}
