package com.apitestagent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.apitestagent.domain.SkillType;
import com.apitestagent.domain.TaskRecord;
import com.apitestagent.domain.TaskStatus;
import com.apitestagent.persistence.entity.AgentTaskArtifactEntity;
import com.apitestagent.persistence.entity.AgentTaskEntity;
import com.apitestagent.persistence.entity.AgentTaskEventEntity;
import com.apitestagent.persistence.mapper.AgentTaskArtifactMapper;
import com.apitestagent.persistence.mapper.AgentTaskEventMapper;
import com.apitestagent.persistence.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class TaskPersistenceService {

    private static final String LIMIT_ONE = "limit 1";
    private static final String TASK_FILE_NAME = "task.json";
    private static final String EXECUTION_EVENTS_FILE_NAME = "execution-events.jsonl";
    private static final String TRACE_SUMMARY_FILE_NAME = "trace-summary.json";

    private final ObjectMapper objectMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final AgentTaskEventMapper agentTaskEventMapper;
    private final AgentTaskArtifactMapper agentTaskArtifactMapper;

    public TaskPersistenceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.agentTaskMapper = null;
        this.agentTaskEventMapper = null;
        this.agentTaskArtifactMapper = null;
    }

    @Autowired
    public TaskPersistenceService(ObjectMapper objectMapper,
                                  ObjectProvider<AgentTaskMapper> agentTaskMapperProvider,
                                  ObjectProvider<AgentTaskEventMapper> agentTaskEventMapperProvider,
                                  ObjectProvider<AgentTaskArtifactMapper> agentTaskArtifactMapperProvider) {
        this.objectMapper = objectMapper;
        this.agentTaskMapper = agentTaskMapperProvider.getIfAvailable();
        this.agentTaskEventMapper = agentTaskEventMapperProvider.getIfAvailable();
        this.agentTaskArtifactMapper = agentTaskArtifactMapperProvider.getIfAvailable();
    }

    public void saveTaskRecord(TaskRecord taskRecord) throws IOException {
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath());
        Files.createDirectories(workspaceDir);
        Files.write(workspaceDir.resolve(TASK_FILE_NAME),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(taskRecord),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        upsertTaskRecord(taskRecord);
        syncArtifacts(taskRecord);
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
        upsertTaskRecord(taskRecord);
        int currentCount = safeInteger(taskRecord.getExecutionEventCount());
        int nextCount = currentCount + 1;
        taskRecord.setExecutionEventCount(nextCount);
        insertExecutionEvent(taskRecord, phase, status, message, details, nextCount);
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
        if (databasePersistenceEnabled()) {
            AgentTaskEntity taskEntity = agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTaskEntity>()
                .eq(AgentTaskEntity::getTaskId, taskId)
                .last(LIMIT_ONE));
            if (taskEntity != null) {
                TaskRecord taskRecord = toTaskRecord(taskEntity);
                taskRecord.setArtifacts(loadArtifactNames(taskId));
                return taskRecord;
            }
        }

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

    public Path resolveArtifactPath(TaskRecord taskRecord, String artifactName) {
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath()).normalize();
        Path artifactPath = workspaceDir.resolve(artifactName).normalize();
        if (!artifactPath.startsWith(workspaceDir)) {
            throw new IllegalArgumentException("非法的 artifactName: " + artifactName);
        }
        return artifactPath;
    }

    private int safeInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private void upsertTaskRecord(TaskRecord taskRecord) {
        if (!databasePersistenceEnabled()) {
            return;
        }
        AgentTaskEntity existing = agentTaskMapper.selectOne(new LambdaQueryWrapper<AgentTaskEntity>()
            .eq(AgentTaskEntity::getTaskId, taskRecord.getTaskId())
            .last(LIMIT_ONE));
        AgentTaskEntity entity = toTaskEntity(taskRecord);
        if (existing == null) {
            agentTaskMapper.insert(entity);
            return;
        }
        entity.setId(existing.getId());
        agentTaskMapper.updateById(entity);
    }

    private void insertExecutionEvent(TaskRecord taskRecord,
                                      String phase,
                                      String status,
                                      String message,
                                      Map<String, Object> details,
                                      int seqNo) {
        if (!databasePersistenceEnabled()) {
            return;
        }
        AgentTaskEventEntity entity = new AgentTaskEventEntity();
        entity.setTaskId(taskRecord.getTaskId());
        entity.setPhase(phase);
        entity.setStatus(status);
        entity.setMessage(message);
        entity.setDetailsJson(toJson(details == null ? new LinkedHashMap<String, Object>() : details));
        entity.setSeqNo(seqNo);
        entity.setCreatedAt(toLocalDateTime(taskRecord.getUpdatedAt()));
        agentTaskEventMapper.insert(entity);
    }

    private void syncArtifacts(TaskRecord taskRecord) {
        if (!databasePersistenceEnabled()) {
            return;
        }
        List<String> artifacts = taskRecord.getArtifacts();
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }
        Path workspaceDir = Paths.get(taskRecord.getWorkspacePath()).normalize();
        for (String artifactName : artifacts) {
            Path artifactPath = workspaceDir.resolve(artifactName).normalize();
            AgentTaskArtifactEntity existing = agentTaskArtifactMapper.selectOne(new LambdaQueryWrapper<AgentTaskArtifactEntity>()
                .eq(AgentTaskArtifactEntity::getTaskId, taskRecord.getTaskId())
                .eq(AgentTaskArtifactEntity::getArtifactName, artifactName)
                .last(LIMIT_ONE));
            AgentTaskArtifactEntity entity = new AgentTaskArtifactEntity();
            entity.setTaskId(taskRecord.getTaskId());
            entity.setArtifactName(artifactName);
            entity.setContentType(resolveArtifactContentType(artifactName));
            entity.setStorageType("FILE");
            entity.setStoragePath(artifactPath.toString());
            entity.setFileSize(Files.exists(artifactPath) ? safeFileSize(artifactPath) : 0L);
            entity.setContentText(null);
            entity.setCreatedAt(toLocalDateTime(taskRecord.getCreatedAt()));
            entity.setUpdatedAt(toLocalDateTime(taskRecord.getUpdatedAt()));
            if (existing == null) {
                agentTaskArtifactMapper.insert(entity);
            } else {
                entity.setId(existing.getId());
                entity.setCreatedAt(existing.getCreatedAt());
                agentTaskArtifactMapper.updateById(entity);
            }
        }
    }

    private List<String> loadArtifactNames(String taskId) {
        if (!databasePersistenceEnabled()) {
            return new ArrayList<>();
        }
        List<AgentTaskArtifactEntity> artifactEntities = agentTaskArtifactMapper.selectList(
            new LambdaQueryWrapper<AgentTaskArtifactEntity>()
                .eq(AgentTaskArtifactEntity::getTaskId, taskId)
                .orderByAsc(AgentTaskArtifactEntity::getCreatedAt)
                .orderByAsc(AgentTaskArtifactEntity::getId));
        return artifactEntities.stream()
            .map(AgentTaskArtifactEntity::getArtifactName)
            .collect(Collectors.toList());
    }

    private String resolveArtifactContentType(String artifactName) {
        if (!StringUtils.hasText(artifactName)) {
            return "text/plain";
        }
        String lowered = artifactName.toLowerCase();
        if (lowered.endsWith(".json") || lowered.endsWith(".jsonl")) {
            return "application/json";
        }
        if (lowered.endsWith(".md")) {
            return "text/markdown";
        }
        return "text/plain";
    }

    private long safeFileSize(Path artifactPath) {
        try {
            return Files.size(artifactPath);
        } catch (IOException ex) {
            return 0L;
        }
    }

    private boolean databasePersistenceEnabled() {
        return agentTaskMapper != null && agentTaskEventMapper != null && agentTaskArtifactMapper != null;
    }

    private AgentTaskEntity toTaskEntity(TaskRecord taskRecord) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setTaskId(taskRecord.getTaskId());
        entity.setSkillCode(taskRecord.getSkillType().getCode());
        entity.setSkillName(taskRecord.getSkillType().getDisplayName());
        entity.setStatus(taskRecord.getStatus().name());
        entity.setPrompt(taskRecord.getPrompt());
        entity.setRequestJson(null);
        entity.setWorkspacePath(taskRecord.getWorkspacePath());
        entity.setMessage(taskRecord.getMessage());
        entity.setLastError(taskRecord.getLastError());
        entity.setSourceLookupApplied(Boolean.TRUE.equals(taskRecord.getSourceLookupApplied()));
        entity.setExecutionEventCount(safeInteger(taskRecord.getExecutionEventCount()));
        entity.setCreatedAt(toLocalDateTime(taskRecord.getCreatedAt()));
        entity.setUpdatedAt(toLocalDateTime(taskRecord.getUpdatedAt()));
        return entity;
    }

    private TaskRecord toTaskRecord(AgentTaskEntity entity) {
        TaskRecord taskRecord = new TaskRecord();
        taskRecord.setTaskId(entity.getTaskId());
        taskRecord.setSkillType(SkillType.valueOf(entity.getSkillCode()));
        taskRecord.setStatus(TaskStatus.valueOf(entity.getStatus()));
        taskRecord.setPrompt(entity.getPrompt());
        taskRecord.setWorkspacePath(entity.getWorkspacePath());
        taskRecord.setMessage(entity.getMessage());
        taskRecord.setLastError(entity.getLastError());
        taskRecord.setSourceLookupApplied(Boolean.TRUE.equals(entity.getSourceLookupApplied()));
        taskRecord.setExecutionEventCount(entity.getExecutionEventCount());
        taskRecord.setCreatedAt(toInstant(entity.getCreatedAt()));
        taskRecord.setUpdatedAt(toInstant(entity.getUpdatedAt()));
        return taskRecord;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("序列化数据库持久化内容失败", ex);
        }
    }
}
