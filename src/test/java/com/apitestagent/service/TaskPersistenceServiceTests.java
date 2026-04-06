package com.apitestagent.service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InOrder;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;

import com.apitestagent.domain.SkillType;
import com.apitestagent.domain.TaskRecord;
import com.apitestagent.domain.TaskStatus;
import com.apitestagent.persistence.entity.AgentTaskEntity;
import com.apitestagent.persistence.entity.AgentTaskEventEntity;
import com.apitestagent.persistence.mapper.AgentTaskArtifactMapper;
import com.apitestagent.persistence.mapper.AgentTaskEventMapper;
import com.apitestagent.persistence.mapper.AgentTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;

class TaskPersistenceServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldPersistTaskBeforeExecutionEventInsertWhenDatabasePersistenceEnabled() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
        AgentTaskEventMapper eventMapper = mock(AgentTaskEventMapper.class);
        AgentTaskArtifactMapper artifactMapper = mock(AgentTaskArtifactMapper.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentTaskMapper> taskMapperProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentTaskEventMapper> eventMapperProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<AgentTaskArtifactMapper> artifactMapperProvider = mock(ObjectProvider.class);
        when(taskMapperProvider.getIfAvailable()).thenReturn(taskMapper);
        when(eventMapperProvider.getIfAvailable()).thenReturn(eventMapper);
        when(artifactMapperProvider.getIfAvailable()).thenReturn(artifactMapper);
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        TaskPersistenceService persistenceService = new TaskPersistenceService(
            objectMapper,
            taskMapperProvider,
            eventMapperProvider,
            artifactMapperProvider
        );

        TaskRecord taskRecord = new TaskRecord();
        taskRecord.setTaskId("task-001");
        taskRecord.setSkillType(SkillType.A1);
        taskRecord.setStatus(TaskStatus.PENDING);
        taskRecord.setPrompt("demo");
        taskRecord.setWorkspacePath(tempDir.resolve("workspace/task-001").toString());
        taskRecord.setArtifacts(Collections.<String>emptyList());
        taskRecord.setCreatedAt(Instant.now());
        taskRecord.setUpdatedAt(taskRecord.getCreatedAt());
        taskRecord.setExecutionEventCount(0);

        persistenceService.appendExecutionEvent(taskRecord, "SUBMITTED", "PENDING", "任务已创建", Collections.<String, Object>emptyMap());

        InOrder inOrder = inOrder(taskMapper, eventMapper);
        inOrder.verify(taskMapper).selectOne(any(LambdaQueryWrapper.class));
        inOrder.verify(taskMapper).insert(any(AgentTaskEntity.class));
        inOrder.verify(eventMapper).insert(any(AgentTaskEventEntity.class));
    }
}