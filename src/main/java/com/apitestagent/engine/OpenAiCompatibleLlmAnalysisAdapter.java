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

    private static final String MESSAGE_FIELD = "message";

    private static final String CONTENT_FIELD = "content";

    private static final String CHOICES_FIELD = "choices";

    private static final String TEXT_FIELD = "text";

    private static final String VALUE_FIELD = "value";

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
        message.put(CONTENT_FIELD, content);
        return message;
    }

    private String buildSystemPrompt(LlmRenderRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are a code-diff and interface-chain testing analysis agent. ");
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
            String upstreamError = extractTextValue(root.path("error").path(MESSAGE_FIELD));
            if (StringUtils.hasText(upstreamError)) {
                throw new IllegalStateException("LLM upstream error: " + upstreamError);
            }
            String content = extractPreferredContent(root);
            if (StringUtils.hasText(content)) {
                return content;
            }
            throw new IllegalStateException(
                "LLM response does not contain supported content fields "
                    + "(choices[0].message.content, choices[0].message.reasoning_content, choices[0].text, output_text)"
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse LLM response", ex);
        }
    }

    private String extractPreferredContent(JsonNode root) {
        String content = extractTextValue(root.path(CHOICES_FIELD).path(0).path(MESSAGE_FIELD).path(CONTENT_FIELD));
        if (StringUtils.hasText(content)) {
            return content;
        }
        content = extractTextValue(root.path(CHOICES_FIELD).path(0).path(MESSAGE_FIELD).path("reasoning_content"));
        if (StringUtils.hasText(content)) {
            return content;
        }
        content = extractTextValue(root.path(CHOICES_FIELD).path(0).path("delta").path(CONTENT_FIELD));
        if (StringUtils.hasText(content)) {
            return content;
        }
        content = extractTextValue(root.path(CHOICES_FIELD).path(0).path(TEXT_FIELD));
        if (StringUtils.hasText(content)) {
            return content;
        }
        content = extractTextValue(root.path("output_text"));
        if (StringUtils.hasText(content)) {
            return content;
        }
        return extractOutputContent(root.path("output"));
    }

    private String extractOutputContent(JsonNode outputNode) {
        if (!outputNode.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : outputNode) {
            appendContent(builder, extractTextValue(item.path(CONTENT_FIELD)));
            appendContent(builder, extractTextValue(item.path(TEXT_FIELD)));
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String extractTextValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return StringUtils.hasText(node.asText()) ? node.asText() : null;
        }
        if (node.isArray()) {
            return collectArrayText(node);
        }
        if (node.isObject()) {
            return firstNonBlank(
                extractTextValue(node.path(TEXT_FIELD)),
                extractTextValue(node.path(CONTENT_FIELD)),
                extractTextValue(node.path(VALUE_FIELD))
            );
        }
        return null;
    }

    private String collectArrayText(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode item : node) {
            appendContent(builder, extractTextValue(item));
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void appendContent(StringBuilder builder, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(content);
    }
}