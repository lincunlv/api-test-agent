package com.apitestagent.web.dto;

import java.util.List;

public class GitHistoryView {

    private String repositoryPath;

    private String currentBranch;

    private String resolvedRef;

    private String searchQuery;

    private Integer pageNumber;

    private Integer pageSize;

    private Integer totalCount;

    private Boolean hasNextPage;

    private List<GitReferenceView> refs;

    private List<GitCommitView> commits;

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }

    public String getCurrentBranch() {
        return currentBranch;
    }

    public void setCurrentBranch(String currentBranch) {
        this.currentBranch = currentBranch;
    }

    public String getResolvedRef() {
        return resolvedRef;
    }

    public void setResolvedRef(String resolvedRef) {
        this.resolvedRef = resolvedRef;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Boolean getHasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(Boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }

    public List<GitReferenceView> getRefs() {
        return refs;
    }

    public void setRefs(List<GitReferenceView> refs) {
        this.refs = refs;
    }

    public List<GitCommitView> getCommits() {
        return commits;
    }

    public void setCommits(List<GitCommitView> commits) {
        this.commits = commits;
    }
}