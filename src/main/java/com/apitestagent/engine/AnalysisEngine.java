package com.apitestagent.engine;

import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;

public interface AnalysisEngine {

    AnalysisExecutionResult generate(CreateTaskRequest request, SkillType skillType, SkillBundle skillBundle, String taskId);
}
