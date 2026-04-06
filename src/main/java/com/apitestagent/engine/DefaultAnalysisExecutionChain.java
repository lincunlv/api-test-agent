package com.apitestagent.engine;

import java.util.List;

public class DefaultAnalysisExecutionChain implements AnalysisExecutionChain {

    private final List<AnalysisMiddleware> middlewares;
    private final AnalysisRenderer renderer;
    private final int index;

    public DefaultAnalysisExecutionChain(List<AnalysisMiddleware> middlewares, AnalysisRenderer renderer) {
        this(middlewares, renderer, 0);
    }

    private DefaultAnalysisExecutionChain(List<AnalysisMiddleware> middlewares, AnalysisRenderer renderer, int index) {
        this.middlewares = middlewares;
        this.renderer = renderer;
        this.index = index;
    }

    @Override
    public AnalysisExecutionResult proceed(AnalysisExecutionContext context) {
        if (index < middlewares.size()) {
            AnalysisMiddleware middleware = middlewares.get(index);
            AnalysisExecutionChain nextChain = new DefaultAnalysisExecutionChain(middlewares, renderer, index + 1);
            return middleware.invoke(context, nextChain);
        }
        String content = renderer.render(context.getRequest(), context.getSkillType(), context.getSkillBundle(), context.getTaskId());
        return new AnalysisExecutionResult(content);
    }
}
