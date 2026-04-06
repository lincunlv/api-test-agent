package com.apitestagent.web.dto;

import com.apitestagent.domain.TaskRecord;

import java.util.List;

public class TaskView {

    private String taskId;
    private String skillCode;
    private String skillName;
    private String status;
    private String prompt;
    private String workspacePath;
    private List<String> artifacts;
    private String message;
    private String lastError;
    private Integer executionEventCount;
    private Boolean sourceLookupApplied;
    private String createdAt;
    private String updatedAt;

    public static TaskView from(TaskRecord taskRecord) {
        TaskView taskView = new TaskView();
        taskView.setTaskId(taskRecord.getTaskId());
        taskView.setSkillCode(taskRecord.getSkillType().getCode());
        taskView.setSkillName(taskRecord.getSkillType().getDisplayName());
        taskView.setStatus(taskRecord.getStatus().name());
        taskView.setPrompt(taskRecord.getPrompt());
        taskView.setWorkspacePath(taskRecord.getWorkspacePath());
        taskView.setArtifacts(taskRecord.getArtifacts());
        taskView.setMessage(taskRecord.getMessage());
        taskView.setLastError(taskRecord.getLastError());
        taskView.setExecutionEventCount(taskRecord.getExecutionEventCount());
        taskView.setSourceLookupApplied(taskRecord.getSourceLookupApplied());
        taskView.setCreatedAt(taskRecord.getCreatedAt().toString());
        taskView.setUpdatedAt(taskRecord.getUpdatedAt().toString());
        return taskView;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
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

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
