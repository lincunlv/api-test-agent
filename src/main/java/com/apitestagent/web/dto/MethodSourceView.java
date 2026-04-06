package com.apitestagent.web.dto;

public class MethodSourceView {

    private String filePath;
    private int startLine;
    private int endLine;
    private String matchedSignature;
    private String sourceCode;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public String getMatchedSignature() {
        return matchedSignature;
    }

    public void setMatchedSignature(String matchedSignature) {
        this.matchedSignature = matchedSignature;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }
}
