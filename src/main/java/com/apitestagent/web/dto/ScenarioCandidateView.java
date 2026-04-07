package com.apitestagent.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ScenarioCandidateView {

    private String scenarioId;

    private String scenarioName;

    private String scenarioType;

    private String entryInterface;

    private List<String> relatedInterfaceChain = new ArrayList<>();

    private String triggerCondition;

    private String businessObject;

    private List<String> sharedKeyHints = new ArrayList<>();

    private String dataFlowHint;

    private List<String> responseFieldHints = new ArrayList<>();

    private List<String> requestBindingHints = new ArrayList<>();

    private List<String> fieldTransferHints = new ArrayList<>();

    private String stateTransitionHint;

    private List<String> dependencyHints = new ArrayList<>();

    private String evidence;

    private String priority;

    public String getScenarioId() {
        return scenarioId;
    }

    public void setScenarioId(String scenarioId) {
        this.scenarioId = scenarioId;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public void setScenarioType(String scenarioType) {
        this.scenarioType = scenarioType;
    }

    public String getEntryInterface() {
        return entryInterface;
    }

    public void setEntryInterface(String entryInterface) {
        this.entryInterface = entryInterface;
    }

    public List<String> getRelatedInterfaceChain() {
        return relatedInterfaceChain;
    }

    public void setRelatedInterfaceChain(List<String> relatedInterfaceChain) {
        this.relatedInterfaceChain = relatedInterfaceChain;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }

    public String getBusinessObject() {
        return businessObject;
    }

    public void setBusinessObject(String businessObject) {
        this.businessObject = businessObject;
    }

    public List<String> getSharedKeyHints() {
        return sharedKeyHints;
    }

    public void setSharedKeyHints(List<String> sharedKeyHints) {
        this.sharedKeyHints = sharedKeyHints;
    }

    public String getDataFlowHint() {
        return dataFlowHint;
    }

    public void setDataFlowHint(String dataFlowHint) {
        this.dataFlowHint = dataFlowHint;
    }

    public List<String> getResponseFieldHints() {
        return responseFieldHints;
    }

    public void setResponseFieldHints(List<String> responseFieldHints) {
        this.responseFieldHints = responseFieldHints;
    }

    public List<String> getRequestBindingHints() {
        return requestBindingHints;
    }

    public void setRequestBindingHints(List<String> requestBindingHints) {
        this.requestBindingHints = requestBindingHints;
    }

    public List<String> getFieldTransferHints() {
        return fieldTransferHints;
    }

    public void setFieldTransferHints(List<String> fieldTransferHints) {
        this.fieldTransferHints = fieldTransferHints;
    }

    public String getStateTransitionHint() {
        return stateTransitionHint;
    }

    public void setStateTransitionHint(String stateTransitionHint) {
        this.stateTransitionHint = stateTransitionHint;
    }

    public List<String> getDependencyHints() {
        return dependencyHints;
    }

    public void setDependencyHints(List<String> dependencyHints) {
        this.dependencyHints = dependencyHints;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }
}