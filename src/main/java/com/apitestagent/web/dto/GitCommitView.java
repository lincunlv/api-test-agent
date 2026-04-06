package com.apitestagent.web.dto;

public class GitCommitView {

    private String hash;

    private String shortHash;

    private String subject;

    private String authorName;

    private String authoredAt;

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getShortHash() {
        return shortHash;
    }

    public void setShortHash(String shortHash) {
        this.shortHash = shortHash;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthoredAt() {
        return authoredAt;
    }

    public void setAuthoredAt(String authoredAt) {
        this.authoredAt = authoredAt;
    }
}