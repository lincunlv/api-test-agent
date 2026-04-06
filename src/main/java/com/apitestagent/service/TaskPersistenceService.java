package com.apitestagent.service;

import com.apitestagent.domain.TaskRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TaskPersistenceService {

    private static final String TASK_FILE_NAME = "task.json";
    private static final String EXECUTION_EVENTS_FILE_NAME = "execution-events.jsonl";
    private static final String TRACE_SUMMARY_FILE_NAME = "trace-summary.json";

    private final ObjectMapper objectMapper;

    public TaskPersistenceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void saveTaskRecord(TaskRecord taskRecord) throws IOException {
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath());
        Files.createDirectories(workspaceDir);
        Files.write(workspaceDir.resolve(TASK_FILE_NAME),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(taskRecord),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    public TaskRecord loadTaskRecord(Path workspaceDir) throws IOException {
        return objectMapper.readValue(workspaceDir.resolve(TASK_FILE_NAME).toFile(), TaskRecord.class);
    }

    public boolean exists(Path workspaceDir) {
        return Files.exists(workspaceDir.resolve(TASK_FILE_NAME));
    }

    public void appendExecutionEvent(TaskRecord taskRecord,
                                     String phase,
                                     String status,
                                     String message,
                                     Map<String, Object> details) throws IOException {
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath());
        Files.createDirectories(workspaceDir);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", taskRecord.getTaskId());
        event.put("phase", phase);
        event.put("status", status);
        event.put("message", message);
        event.put("details", details == null ? new LinkedHashMap<String, Object>() : details);
        event.put("createdAt", taskRecord.getUpdatedAt().toString());
        String jsonLine = objectMapper.writeValueAsString(event) + System.lineSeparator();
        Files.write(workspaceDir.resolve(EXECUTION_EVENTS_FILE_NAME),
            jsonLine.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
        int currentCount = safeInteger(taskRecord.getExecutionEventCount());
        taskRecord.setExecutionEventCount(currentCount + 1);
    }

    public void writeTraceSummary(TaskRecord taskRecord) throws IOException {
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath());
        Map<String, Object> traceSummary = new LinkedHashMap<>();
        traceSummary.put("taskId", taskRecord.getTaskId());
        traceSummary.put("skill", taskRecord.getSkillType().getCode());
        traceSummary.put("status", taskRecord.getStatus().name());
        traceSummary.put("artifacts", taskRecord.getArtifacts());
        traceSummary.put("executionEventCount", taskRecord.getExecutionEventCount());
        traceSummary.put("sourceLookupApplied", taskRecord.getSourceLookupApplied());
        traceSummary.put("message", taskRecord.getMessage());
        traceSummary.put("lastError", taskRecord.getLastError());
        traceSummary.put("updatedAt", taskRecord.getUpdatedAt().toString());
        Files.write(workspaceDir.resolve(TRACE_SUMMARY_FILE_NAME),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(traceSummary),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
    }

    public TaskRecord loadTaskRecordByTaskId(String workspaceBaseDir, String taskId) throws IOException {
        Path baseDir = Paths.get(workspaceBaseDir);
        if (!Files.exists(baseDir)) {
            return null;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(baseDir, 3)) {
            Path taskFile = stream
                .filter(path -> path.getFileName().toString().equals(TASK_FILE_NAME))
                .filter(path -> path.getParent() != null && path.getParent().getFileName().toString().equals(taskId))
                .findFirst()
                .orElse(null);
            if (taskFile == null) {
                return null;
            }
            return objectMapper.readValue(taskFile.toFile(), TaskRecord.class);
        }
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
