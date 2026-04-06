package com.apitestagent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.apitestagent.config.AgentStorageProperties;
import com.apitestagent.domain.TaskRecord;
import com.apitestagent.engine.AnalysisEngine;
import com.apitestagent.web.dto.CreateTaskRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

class AgentTaskServiceFailureTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistFailedTaskAndExecutionEventsWhenAnalysisFails() throws Exception {
        Path workspaceBaseDir = tempDir.resolve("workspace/agent_files");
        Path skillsBaseDir = tempDir.resolve("skills/interface-chain-analyzer");
        createSkillFixtures(skillsBaseDir);

        AgentStorageProperties properties = new AgentStorageProperties();
        properties.setWorkspaceBaseDir(workspaceBaseDir.toString());
        properties.setSkillsBaseDir(skillsBaseDir.toString());

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        SkillService skillService = new SkillService(properties);
        TaskPersistenceService persistenceService = new TaskPersistenceService(objectMapper);
        GitDiffService gitDiffService = new GitDiffService();
        MethodSourceService methodSourceService = new MethodSourceService();
        AnalysisEngine failingEngine = (requestValue, skillType, skillBundle, taskId) -> {
            throw new IllegalStateException("simulated analysis failure");
        };

        AgentTaskService agentTaskService = new AgentTaskService(
            properties,
            skillService,
            failingEngine,
            gitDiffService,
            methodSourceService,
            persistenceService,
            objectMapper
        );

        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskType("A3");
        request.setPrompt("失败场景测试");
        Map<String, Object> chainData = new LinkedHashMap<>();
        Map<String, Object> interfaceData = new LinkedHashMap<>();
        interfaceData.put("name", "demoFailure");
        interfaceData.put("path", "/demo/failure");
        interfaceData.put("method", "POST");
        chainData.put("interface", interfaceData);
        request.setChainData(chainData);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> agentTaskService.createTask(request));
        assertTrue(exception.getMessage().contains("simulated analysis failure"));

        Path taskDir = findSingleTaskDirectory(workspaceBaseDir);
        assertNotNull(taskDir);

        TaskRecord taskRecord = persistenceService.loadTaskRecord(taskDir);
        assertEquals("FAILED", taskRecord.getStatus().name());
        assertEquals("simulated analysis failure", taskRecord.getLastError());
        assertTrue(taskRecord.getExecutionEventCount() >= 4);

        String executionEvents = new String(Files.readAllBytes(taskDir.resolve("execution-events.jsonl")), StandardCharsets.UTF_8);
        String traceSummary = new String(Files.readAllBytes(taskDir.resolve("trace-summary.json")), StandardCharsets.UTF_8);
        assertTrue(executionEvents.contains("FAILED"));
        assertTrue(executionEvents.contains("SOURCE_LOOKUP_SKIPPED"));
        assertTrue(traceSummary.contains("FAILED"));
        assertTrue(traceSummary.contains("simulated analysis failure"));
    }

    private void createSkillFixtures(Path skillsBaseDir) throws IOException {
        Files.createDirectories(skillsBaseDir.resolve("references"));
        Files.write(skillsBaseDir.resolve("SKILL.md"), "skill-index".getBytes(StandardCharsets.UTF_8));
        Files.write(skillsBaseDir.resolve("references/A1-logic.md"), "a1".getBytes(StandardCharsets.UTF_8));
        Files.write(skillsBaseDir.resolve("references/A2-bug.md"), "a2".getBytes(StandardCharsets.UTF_8));
        Files.write(skillsBaseDir.resolve("references/A3-cases.md"), "a3".getBytes(StandardCharsets.UTF_8));
        Files.write(skillsBaseDir.resolve("references/A4-diff.md"), "a4".getBytes(StandardCharsets.UTF_8));
        Files.write(skillsBaseDir.resolve("references/A5-doc.md"), "a5".getBytes(StandardCharsets.UTF_8));
    }

    private Path findSingleTaskDirectory(Path workspaceBaseDir) throws IOException {
        try (Stream<Path> stream = Files.walk(workspaceBaseDir)) {
            return stream
                .filter(path -> path.getFileName() != null && path.getFileName().toString().equals("task.json"))
                .map(Path::getParent)
                .findFirst()
                .orElse(null);
        }
    }
}