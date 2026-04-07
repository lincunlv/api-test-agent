package com.apitestagent.web.dto;

import java.util.ArrayList;
import java.util.List;

public class GitDiffView {

    private String repositoryPath;

    private String diffRange;

    private Boolean cached;

    private Boolean truncated;

    private List<String> changedFiles = new ArrayList<>();

    private List<String> changedClasses = new ArrayList<>();

    private List<String> changedMethods = new ArrayList<>();

    private List<RelatedInterfaceView> relatedInterfaces = new ArrayList<>();

    private List<ScenarioCandidateView> scenarioCandidates = new ArrayList<>();

    private String diffOutput;

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public String getDiffRange() {
        return diffRange;
    }

    public void setDiffRange(String diffRange) {
        this.diffRange = diffRange;
    }

    public Boolean getCached() {
        return cached;
    }

    public void setCached(Boolean cached) {
        this.cached = cached;
    }

    public Boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(Boolean truncated) {
        this.truncated = truncated;
    }

    public List<String> getChangedFiles() {
        return changedFiles;
    }

    public void setChangedFiles(List<String> changedFiles) {
        this.changedFiles = changedFiles;
    }

    public List<String> getChangedClasses() {
        return changedClasses;
    }

    public void setChangedClasses(List<String> changedClasses) {
        this.changedClasses = changedClasses;
    }

    public List<String> getChangedMethods() {
        return changedMethods;
    }

    public void setChangedMethods(List<String> changedMethods) {
        this.changedMethods = changedMethods;
    }

    public List<RelatedInterfaceView> getRelatedInterfaces() {
        return relatedInterfaces;
    }

    public void setRelatedInterfaces(List<RelatedInterfaceView> relatedInterfaces) {
        this.relatedInterfaces = relatedInterfaces;
    }

    public List<ScenarioCandidateView> getScenarioCandidates() {
        return scenarioCandidates;
    }

    public void setScenarioCandidates(List<ScenarioCandidateView> scenarioCandidates) {
        this.scenarioCandidates = scenarioCandidates;
    }

    public String getDiffOutput() {
        return diffOutput;
    }

    public void setDiffOutput(String diffOutput) {
        this.diffOutput = diffOutput;
    }
}