package com.apitestagent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TaskRecord {

    private String taskId;
    private SkillType skillType;
    private TaskStatus status;
    private String prompt;
    private String workspacePath;
    private List<String> artifacts = new ArrayList<>();
    private String message;
    private String lastError;
    private Integer executionEventCount = 0;
    private Boolean sourceLookupApplied = Boolean.FALSE;
    private Instant createdAt;
    private Instant updatedAt;

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

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getWorkspacePath() {
        return workspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.workspacePath = workspacePath;
    }

    public List<String> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<String> artifacts) {
        this.artifacts = artifacts;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Integer getExecutionEventCount() {
        return executionEventCount;
    }

    public void setExecutionEventCount(Integer executionEventCount) {
        this.executionEventCount = executionEventCount;
    }

    public Boolean getSourceLookupApplied() {
        return sourceLookupApplied;
    }

    public void setSourceLookupApplied(Boolean sourceLookupApplied) {
        this.sourceLookupApplied = sourceLookupApplied;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
