package com.apitestagent.engine;

import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnalysisExecutionContext {

    private final String taskId;
    private final CreateTaskRequest request;
    private final SkillType skillType;
    private final SkillBundle skillBundle;
    private final Map<String, Object> attributes = new LinkedHashMap<>();
    private int attempt = 1;

    public AnalysisExecutionContext(String taskId,
                                    CreateTaskRequest request,
                                    SkillType skillType,
                                    SkillBundle skillBundle) {
        this.taskId = taskId;
        this.request = request;
        this.skillType = skillType;
        this.skillBundle = skillBundle;
    }

    public String getTaskId() {
        return taskId;
    }

    public CreateTaskRequest getRequest() {
        return request;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public SkillBundle getSkillBundle() {
        return skillBundle;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }
}
