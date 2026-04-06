package com.apitestagent.web.dto;

import java.util.ArrayList;
import java.util.List;

public class GitDiffQueryRequest {

    private String repositoryPath;

    private String diffRange;

    private Boolean cached = Boolean.FALSE;

    private Integer maxCharacters = Integer.valueOf(20000);

    private List<String> pathspecs = new ArrayList<>();

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

    public Integer getMaxCharacters() {
        return maxCharacters;
    }

    public void setMaxCharacters(Integer maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public List<String> getPathspecs() {
        return pathspecs;
    }

    public void setPathspecs(List<String> pathspecs) {
        this.pathspecs = pathspecs;
    }
}