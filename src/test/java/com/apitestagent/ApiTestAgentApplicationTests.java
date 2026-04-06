package com.apitestagent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTestAgentApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void shouldCreateTaskAndQueryTaskDetail() throws Exception {
        String requestBody = "{"
            + "\"taskType\":\"A3\"," 
            + "\"prompt\":\"请生成测试用例\"," 
            + "\"chainData\":{"
            + "\"interface\":{\"name\":\"createTask\",\"path\":\"/api/agent/tasks\",\"method\":\"POST\"},"
            + "\"entry\":{\"class_name\":\"AgentTaskService\",\"method_name\":\"createTask\",\"package_name\":\"com.apitestagent.service\",\"parameter_count\":1}"
            + "}"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillCode").value("A3"))
            .andExpect(jsonPath("$.status").value("PARTIAL_SUCCESS"))
            .andExpect(jsonPath("$.sourceLookupApplied").value(true))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        String taskId = jsonNode.get("taskId").asText();
        assertTrue(jsonNode.get("artifacts").toString().contains("source-context.md"));

        String queryResponse = mockMvc.perform(get("/api/agent/tasks/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.executionEventCount").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode queryJson = objectMapper.readTree(queryResponse);
        assertTrue(queryJson.get("message").asText().contains("结构化草稿"));
    }

    @Test
    void shouldLocateMethodSourceWithEnhancedQuery() throws Exception {
        String requestBody = "{"
            + "\"className\":\"AgentTaskService\"," 
            + "\"methodName\":\"createTask\"," 
            + "\"packageName\":\"com.apitestagent.service\"," 
            + "\"parameterCount\":1"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tools/get-method-source")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertTrue(jsonNode.get("filePath").asText().contains("AgentTaskService.java"));
        assertTrue(jsonNode.get("matchedSignature").asText().contains("createTask"));
        assertTrue(jsonNode.get("sourceCode").asText().contains("public TaskView createTask"));
    }

    @Test
    void shouldFetchGitDiffFromTemporaryRepository() throws Exception {
        Path repositoryPath = createTempJavaApiRepository();
        Files.write(repositoryPath.resolve(Paths.get("src", "main", "java", "com", "example", "service", "OrderService.java")),
            Arrays.asList(
                "package com.example.service;",
                "",
                "public class OrderService {",
                "    public String createOrder(String request) {",
                "        return \"updated\";",
                "    }",
                "}"),
            StandardCharsets.UTF_8);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repositoryPath", repositoryPath.toString());
        String requestJson = objectMapper.writeValueAsString(request);

        String response = mockMvc.perform(post("/api/agent/tools/get-git-diff")
                .contentType("application/json")
            .content(Objects.requireNonNull(requestJson)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertEquals(repositoryPath.toAbsolutePath().normalize().toString(), jsonNode.get("repositoryPath").asText());
        assertTrue(jsonNode.get("changedFiles").toString().contains("OrderService.java"));
        assertTrue(jsonNode.get("changedMethods").toString().contains("createOrder"));
        assertTrue(jsonNode.get("relatedInterfaces").toString().contains("/orders"));
        assertTrue(jsonNode.get("diffOutput").asText().contains("updated"));
    }

    @Test
    void shouldUseMockRendererAndPersistExecutionMetadata() throws Exception {
        String requestBody = "{"
            + "\"taskType\":\"A5\"," 
            + "\"prompt\":\"请生成接口文档\"," 
            + "\"chainData\":{"
            + "\"interface\":{\"name\":\"mockApi\",\"path\":\"/mock/api\",\"method\":\"GET\"}"
            + "},"
            + "\"options\":{"
            + "\"rendererMode\":\"mock-model\","
            + "\"mockOutput\":\"# mock-output\\n\\nmock renderer content\""
            + "}"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillCode").value("A5"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        Path workspacePath = Paths.get(jsonNode.get("workspacePath").asText());
        String artifactContent = new String(Files.readAllBytes(workspacePath.resolve("api-doc.md")), StandardCharsets.UTF_8);
        String traceSummary = new String(Files.readAllBytes(workspacePath.resolve("trace-summary.json")), StandardCharsets.UTF_8);
        String executionEvents = new String(Files.readAllBytes(workspacePath.resolve("execution-events.jsonl")), StandardCharsets.UTF_8);

        assertTrue(artifactContent.contains("mock renderer content"));
        assertTrue(traceSummary.contains("PARTIAL_SUCCESS"));
        assertTrue(executionEvents.contains("ARTIFACT_WRITTEN"));
        assertTrue(executionEvents.contains("durationMs"));
        assertTrue(executionEvents.contains("contextLength"));
        assertTrue(eventIndex(executionEvents, "SUBMITTED") < eventIndex(executionEvents, "STATUS_CHANGED"));
        assertTrue(eventIndex(executionEvents, "STATUS_CHANGED") < eventIndex(executionEvents, "INPUT_PERSISTED"));
        assertTrue(eventIndex(executionEvents, "INPUT_PERSISTED") < eventIndex(executionEvents, "SKILL_LOADED"));
        assertTrue(eventIndex(executionEvents, "SKILL_LOADED") < eventIndex(executionEvents, "ARTIFACT_WRITTEN"));
        assertTrue(eventIndex(executionEvents, "ARTIFACT_WRITTEN") < eventIndex(executionEvents, "COMPLETED"));
        assertEquals("A5", jsonNode.get("skillCode").asText());
    }

    @Test
    void shouldCreateA4TaskAndPersistGitDiffArtifact() throws Exception {
        Path repositoryPath = createTempJavaApiRepository();
        Files.write(repositoryPath.resolve(Paths.get("src", "main", "java", "com", "example", "service", "OrderService.java")),
            Arrays.asList(
                "package com.example.service;",
                "",
                "public class OrderService {",
                "    public String createOrder(String request) {",
                "        return \"updated\";",
                "    }",
                "}"),
            StandardCharsets.UTF_8);

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("class_name", "AgentTaskService");
        entry.put("method_name", "createTask");
        entry.put("package_name", "com.apitestagent.service");
        entry.put("parameter_count", 1);

        Map<String, Object> interfaceInfo = new LinkedHashMap<>();
        interfaceInfo.put("name", "createTask");
        interfaceInfo.put("path", "/api/agent/tasks");
        interfaceInfo.put("method", "POST");

        Map<String, Object> chainData = new LinkedHashMap<>();
        chainData.put("interface", interfaceInfo);
        chainData.put("entry", entry);

        Map<String, Object> gitDiffQuery = new LinkedHashMap<>();
        gitDiffQuery.put("repositoryPath", repositoryPath.toString());

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("rendererMode", "mock-model");
        options.put("mockOutput", "# diff-cases\n\nmock diff output");
        options.put("gitDiffQuery", gitDiffQuery);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("taskType", "A4");
        request.put("prompt", "请根据 diff 生成关联接口用例");
        request.put("chainData", chainData);
        request.put("options", options);
        String requestJson = objectMapper.writeValueAsString(request);

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
            .content(Objects.requireNonNull(requestJson)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.skillCode").value("A4"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        Path workspacePath = Paths.get(jsonNode.get("workspacePath").asText());
        String artifactContent = new String(Files.readAllBytes(workspacePath.resolve("diff-test-cases.md")), StandardCharsets.UTF_8);
        String gitDiffContent = new String(Files.readAllBytes(workspacePath.resolve("git-diff.patch")), StandardCharsets.UTF_8);
        String diffImpactContent = new String(Files.readAllBytes(workspacePath.resolve("diff-impact.json")), StandardCharsets.UTF_8);

        assertTrue(artifactContent.contains("mock diff output"));
        assertTrue(gitDiffContent.contains("updated"));
        assertTrue(diffImpactContent.contains("/orders"));
        assertTrue(diffImpactContent.contains("createOrder"));
        assertTrue(jsonNode.get("artifacts").toString().contains("git-diff.patch"));
        assertTrue(jsonNode.get("artifacts").toString().contains("diff-impact.json"));
    }

    @Test
    void shouldReturnValidationErrorWhenTaskTypeMissing() throws Exception {
        String requestBody = "{"
            + "\"prompt\":\"缺少任务类型\"," 
            + "\"chainData\":{\"interface\":{\"name\":\"demo\"}}"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertEquals("VALIDATION_ERROR", jsonNode.get("code").asText());
        assertTrue(jsonNode.get("message").asText().contains("taskType"));
    }

    @Test
    void shouldReturnNotFoundWhenTaskDoesNotExist() throws Exception {
        String response = mockMvc.perform(get("/api/agent/tasks/{taskId}", "missing-task-id"))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertEquals("NOT_FOUND", jsonNode.get("code").asText());
        assertTrue(jsonNode.get("message").asText().contains("任务不存在"));
    }

    @Test
    void shouldReturnBadRequestWhenTaskTypeUnsupported() throws Exception {
        String requestBody = "{"
            + "\"taskType\":\"UNKNOWN\"," 
            + "\"prompt\":\"非法任务类型\"," 
            + "\"chainData\":{"
            + "\"interface\":{\"name\":\"unknownApi\",\"path\":\"/unknown\",\"method\":\"GET\"}"
            + "}"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertEquals("BAD_REQUEST", jsonNode.get("code").asText());
        assertTrue(jsonNode.get("message").asText().contains("无法识别的 taskType"));
    }

    @Test
    void shouldReturnInternalErrorWhenRendererFails() throws Exception {
        String requestBody = "{"
            + "\"taskType\":\"A1\"," 
            + "\"prompt\":\"触发内部异常\"," 
            + "\"chainData\":{"
            + "\"interface\":{\"name\":\"failureApi\",\"path\":\"/failure\",\"method\":\"GET\"}"
            + "},"
            + "\"options\":{"
            + "\"rendererMode\":\"mock-failure\","
            + "\"mockFailureMessage\":\"forced renderer failure\""
            + "}"
            + "}";

        String response = mockMvc.perform(post("/api/agent/tasks")
                .contentType("application/json")
                .content(requestBody))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode jsonNode = objectMapper.readTree(response);
        assertEquals("INTERNAL_ERROR", jsonNode.get("code").asText());
        assertTrue(jsonNode.get("message").asText().contains("forced renderer failure"));
    }

    private int eventIndex(String executionEvents, String phase) {
        return executionEvents.indexOf("\"phase\":\"" + phase + "\"");
    }

    private Path createTempJavaApiRepository() throws Exception {
        Path repositoryPath = Files.createTempDirectory("api-test-agent-java-git-");
        runGitCommand(repositoryPath, "git", "init");
        runGitCommand(repositoryPath, "git", "config", "user.email", "tester@example.com");
        runGitCommand(repositoryPath, "git", "config", "user.name", "tester");

        Path controllerFile = repositoryPath.resolve(Paths.get("src", "main", "java", "com", "example", "web", "OrderController.java"));
        Path serviceFile = repositoryPath.resolve(Paths.get("src", "main", "java", "com", "example", "service", "OrderService.java"));
        Files.createDirectories(controllerFile.getParent());
        Files.createDirectories(serviceFile.getParent());

        Files.write(controllerFile,
            Arrays.asList(
                "package com.example.web;",
                "",
                "import org.springframework.web.bind.annotation.PostMapping;",
                "import org.springframework.web.bind.annotation.RequestBody;",
                "import org.springframework.web.bind.annotation.RequestMapping;",
                "import org.springframework.web.bind.annotation.RestController;",
                "",
                "import com.example.service.OrderService;",
                "",
                "@RestController",
                "@RequestMapping(\"/orders\")",
                "public class OrderController {",
                "    private final OrderService orderService = new OrderService();",
                "",
                "    @PostMapping",
                "    public String createOrder(@RequestBody String request) {",
                "        return orderService.createOrder(request);",
                "    }",
                "}"),
            StandardCharsets.UTF_8);

        Files.write(serviceFile,
            Arrays.asList(
                "package com.example.service;",
                "",
                "public class OrderService {",
                "    public String createOrder(String request) {",
                "        return \"created\";",
                "    }",
                "}"),
            StandardCharsets.UTF_8);

        runGitCommand(repositoryPath, "git", "add", ".");
        runGitCommand(repositoryPath, "git", "commit", "-m", "init java api");
        return repositoryPath;
    }

    private void runGitCommand(Path repositoryPath, String... command) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repositoryPath.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readOutput(process.getInputStream());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("git 命令执行失败: " + output);
        }
    }

    private String readOutput(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
}
