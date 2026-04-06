package com.apitestagent.engine;

import com.apitestagent.config.AnalysisProperties;
import com.apitestagent.domain.SkillType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiCompatibleLlmAnalysisAdapterTests {

    @Test
    void shouldCallOpenAiCompatibleEndpointAndExtractContent() {
        AnalysisProperties properties = new AnalysisProperties();
        properties.setLlmBaseUrl("https://mock-llm.local");
        properties.setLlmPath("/v1/chat/completions");
        properties.setLlmApiKey("test-key");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://mock-llm.local/v1/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
            .andExpect(content().contentType("application/json"))
            .andExpect(this::assertRequestBody)
            .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"llm content\"}}]}", MediaType.APPLICATION_JSON));

        OpenAiCompatibleLlmAnalysisAdapter adapter = new OpenAiCompatibleLlmAnalysisAdapter(restTemplate, properties, new ObjectMapper());
        String content = adapter.render(sampleRequest());

        assertEquals("llm content", content);
        server.verify();
    }

    @Test
    void shouldFailFastWhenBaseUrlMissing() {
        AnalysisProperties properties = new AnalysisProperties();
        RestTemplate restTemplate = new RestTemplate();
        OpenAiCompatibleLlmAnalysisAdapter adapter = new OpenAiCompatibleLlmAnalysisAdapter(restTemplate, properties, new ObjectMapper());

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> renderSampleRequest(adapter));
        assertTrue(exception.getMessage().contains("LLM base URL is not configured"));
    }

    private void renderSampleRequest(OpenAiCompatibleLlmAnalysisAdapter adapter) {
        adapter.render(sampleRequest());
    }

    private void assertRequestBody(org.springframework.http.client.ClientHttpRequest request) {
        String requestBody = ((MockClientHttpRequest) request).getBodyAsString();
        assertTrue(requestBody.contains("mock-llm-model"));
        assertTrue(requestBody.contains("正常请求"));
    }

    private LlmRenderRequest sampleRequest() {
        LlmRenderRequest request = new LlmRenderRequest();
        request.setTaskId("task-http-llm");
        request.setSkillType(SkillType.A1);
        request.setModel("mock-llm-model");
        request.setPrompt("正常请求");
        request.setSkillIndexContent("skill-index");
        request.setReferenceContent("reference-content");
        request.setChainData(Collections.<String, Object>singletonMap("interface", Collections.singletonMap("name", "demo")));
        request.setOptions(Collections.<String, Object>singletonMap("rendererMode", "llm"));
        return request;
    }
}