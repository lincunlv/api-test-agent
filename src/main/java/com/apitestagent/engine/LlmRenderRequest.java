package com.apitestagent.engine;

import com.apitestagent.domain.SkillType;

import java.util.Collections;
import java.util.Map;

public class LlmRenderRequest {

    private String taskId;
    private SkillType skillType;
    private String model;
    private String prompt;
    private String skillIndexContent;
    private String referenceContent;
    private Map<String, Object> chainData = Collections.emptyMap();
    private Map<String, Object> options = Collections.emptyMap();

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public SkillType getSkillType() {
        return skillType;
    }

    public void setSkillType(SkillType skillType) {
        this.skillType = skillType;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getSkillIndexContent() {
        return skillIndexContent;
    }

    public void setSkillIndexContent(String skillIndexContent) {
        this.skillIndexContent = skillIndexContent;
    }

    public String getReferenceContent() {
        return referenceContent;
    }

    public void setReferenceContent(String referenceContent) {
        this.referenceContent = referenceContent;
    }

    public Map<String, Object> getChainData() {
        return chainData;
    }

    public void setChainData(Map<String, Object> chainData) {
        this.chainData = chainData == null ? Collections.<String, Object>emptyMap() : chainData;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options == null ? Collections.<String, Object>emptyMap() : options;
    }
}
