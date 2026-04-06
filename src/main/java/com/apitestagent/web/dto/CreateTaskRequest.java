package com.apitestagent.web.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.Map;

public class CreateTaskRequest {

    @NotBlank(message = "不能为空")
    private String taskType;

    @NotBlank(message = "不能为空")
    private String prompt;

    @NotNull(message = "不能为空")
    private Map<String, Object> chainData = new LinkedHashMap<>();

    private Map<String, Object> options = new LinkedHashMap<>();

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Map<String, Object> getChainData() {
        return chainData;
    }

    public void setChainData(Map<String, Object> chainData) {
        this.chainData = chainData;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }
}
