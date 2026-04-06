package com.apitestagent.engine;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.apitestagent.config.AnalysisProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenAiCompatibleLlmAnalysisAdapter implements LlmAnalysisAdapter {

    private final RestTemplate restTemplate;

    private final AnalysisProperties properties;

    private final ObjectMapper objectMapper;

    @Autowired
    public OpenAiCompatibleLlmAnalysisAdapter(RestTemplateBuilder restTemplateBuilder,
                                              AnalysisProperties properties,
                                              ObjectMapper objectMapper) {
        this(restTemplateBuilder
            .setConnectTimeout(Duration.ofMillis(properties.getLlmConnectTimeoutMs()))
            .setReadTimeout(Duration.ofMillis(properties.getLlmReadTimeoutMs()))
            .build(), properties, objectMapper);
    }

    OpenAiCompatibleLlmAnalysisAdapter(RestTemplate restTemplate,
                                       AnalysisProperties properties,
                                       ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String render(LlmRenderRequest request) {
        validateRequest(request);
        URI requestUri = Objects.requireNonNull(URI.create(buildRequestUrl()), "requestUri");
        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> httpEntity = new HttpEntity<>(buildRequestBody(request), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(requestUri, HttpMethod.POST, httpEntity, String.class);
            return extractContent(response.getBody());
        } catch (RestClientException ex) {
            throw new IllegalStateException("LLM request failed: " + ex.getMessage(), ex);
        }
    }

    private void validateRequest(LlmRenderRequest request) {
        if (!StringUtils.hasText(properties.getLlmBaseUrl())) {
            throw new IllegalStateException("LLM base URL is not configured.");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new IllegalStateException("LLM model is not configured.");
        }
    }

    private String buildRequestUrl() {
        String baseUrl = properties.getLlmBaseUrl().trim();
        String path = properties.getLlmPath();
        if (!StringUtils.hasText(path)) {
            return baseUrl;
        }
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1) + path;
        }
        if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String apiKey = properties.getLlmApiKey();
        if (StringUtils.hasText(apiKey)) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }
        return headers;
    }

    private Map<String, Object> buildRequestBody(LlmRenderRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel());
        body.put("temperature", properties.getLlmTemperature());
        body.put("max_tokens", properties.getLlmMaxTokens());
        body.put("messages", buildMessages(request));
        return body;
    }

    private List<Map<String, String>> buildMessages(LlmRenderRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt(request)));
        messages.add(message("user", buildUserPrompt(request)));
        return messages;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildSystemPrompt(LlmRenderRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are an interface testing analysis agent. ");
        builder.append("Follow the provided skill index and reference strictly.\n\n");
        builder.append("[SKILL INDEX]\n").append(request.getSkillIndexContent()).append("\n\n");
        builder.append("[REFERENCE]\n").append(request.getReferenceContent());
        return builder.toString();
    }

    private String buildUserPrompt(LlmRenderRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("taskId: ").append(request.getTaskId()).append("\n");
        builder.append("skill: ").append(request.getSkillType().getCode()).append("\n");
        builder.append("prompt: ").append(request.getPrompt()).append("\n\n");
        builder.append("chainData:\n").append(writeJson(request.getChainData())).append("\n\n");
        if (!request.getOptions().isEmpty()) {
            builder.append("options:\n").append(writeJson(request.getOptions())).append("\n");
        }
        return builder.toString();
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize LLM request context", ex);
        }
    }

    private String extractContent(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException("LLM response body is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            if (!contentNode.isMissingNode() && StringUtils.hasText(contentNode.asText())) {
                return contentNode.asText();
            }
            throw new IllegalStateException("LLM response does not contain choices[0].message.content");
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse LLM response", ex);
        }
    }
}