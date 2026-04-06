package com.apitestagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.analysis")
public class AnalysisProperties {

    private String rendererMode = "template";

    private String llmModel = "";

    private String llmBaseUrl = "";

    private String llmApiKey = "";

    private String llmPath = "";

    private int llmConnectTimeoutMs = 5000;

    private int llmReadTimeoutMs = 60000;

    private double llmTemperature = 0.2D;

    private int llmMaxTokens = 2000;

    private int maxPromptLength = 4000;

    private int maxContextCharacters = 12000;

    private int retryMaxAttempts = 2;

    private long retryBackoffMs = 150L;

    public String getRendererMode() {
        return rendererMode;
    }

    public void setRendererMode(String rendererMode) {
        this.rendererMode = rendererMode;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmBaseUrl() {
        return llmBaseUrl;
    }

    public void setLlmBaseUrl(String llmBaseUrl) {
        this.llmBaseUrl = llmBaseUrl;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public void setLlmApiKey(String llmApiKey) {
        this.llmApiKey = llmApiKey;
    }

    public String getLlmPath() {
        return llmPath;
    }

    public void setLlmPath(String llmPath) {
        this.llmPath = llmPath;
    }

    public int getLlmConnectTimeoutMs() {
        return llmConnectTimeoutMs;
    }

    public void setLlmConnectTimeoutMs(int llmConnectTimeoutMs) {
        this.llmConnectTimeoutMs = llmConnectTimeoutMs;
    }

    public int getLlmReadTimeoutMs() {
        return llmReadTimeoutMs;
    }

    public void setLlmReadTimeoutMs(int llmReadTimeoutMs) {
        this.llmReadTimeoutMs = llmReadTimeoutMs;
    }

    public double getLlmTemperature() {
        return llmTemperature;
    }

    public void setLlmTemperature(double llmTemperature) {
        this.llmTemperature = llmTemperature;
    }

    public int getLlmMaxTokens() {
        return llmMaxTokens;
    }

    public void setLlmMaxTokens(int llmMaxTokens) {
        this.llmMaxTokens = llmMaxTokens;
    }

    public int getMaxPromptLength() {
        return maxPromptLength;
    }

    public void setMaxPromptLength(int maxPromptLength) {
        this.maxPromptLength = maxPromptLength;
    }

    public int getMaxContextCharacters() {
        return maxContextCharacters;
    }

    public void setMaxContextCharacters(int maxContextCharacters) {
        this.maxContextCharacters = maxContextCharacters;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }
}