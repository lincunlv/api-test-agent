package com.apitestagent.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.apitestagent.config.AgentStorageProperties;
import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.domain.TaskRecord;
import com.apitestagent.domain.TaskStatus;
import com.apitestagent.engine.AnalysisEngine;
import com.apitestagent.engine.AnalysisExecutionResult;
import com.apitestagent.web.dto.ArtifactContentView;
import com.apitestagent.web.dto.CreateTaskRequest;
import com.apitestagent.web.dto.GitDiffQueryRequest;
import com.apitestagent.web.dto.GitDiffView;
import com.apitestagent.web.dto.MethodSourceQueryRequest;
import com.apitestagent.web.dto.MethodSourceView;
import com.apitestagent.web.dto.TaskView;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AgentTaskService {

    private static final String INPUT_FILE_NAME = "input.json";
    private static final String GIT_DIFF_FILE_NAME = "git-diff.patch";
    private static final String DIFF_IMPACT_FILE_NAME = "diff-impact.json";
    private static final String SOURCE_CONTEXT_FILE_NAME = "source-context.md";
    private static final String TRACE_SUMMARY_FILE_NAME = "trace-summary.json";
    private static final int MAX_LLM_DIFF_OUTPUT_CHARACTERS = 3000;

    private final ConcurrentMap<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    private final AgentStorageProperties properties;

    private final SkillService skillService;

    private final AnalysisEngine analysisEngine;

    private final GitDiffService gitDiffService;

    private final MethodSourceService methodSourceService;

    private final TaskPersistenceService taskPersistenceService;

    private final ObjectMapper objectMapper;

    public AgentTaskService(AgentStorageProperties properties,
                            SkillService skillService,
                            AnalysisEngine analysisEngine,
                            GitDiffService gitDiffService,
                            MethodSourceService methodSourceService,
                            TaskPersistenceService taskPersistenceService,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.skillService = skillService;
        this.analysisEngine = analysisEngine;
        this.gitDiffService = gitDiffService;
        this.methodSourceService = methodSourceService;
        this.taskPersistenceService = taskPersistenceService;
        this.objectMapper = objectMapper;
    }

    public TaskView createTask(CreateTaskRequest request) throws IOException {
        SkillType skillType = SkillType.fromTaskType(request.getTaskType());
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Path workspaceDir = Paths.get(properties.getWorkspaceBaseDir(), skillType.getCode().toLowerCase(), taskId);
        Files.createDirectories(workspaceDir);

        TaskRecord taskRecord = new TaskRecord();
        taskRecord.setTaskId(taskId);
        taskRecord.setSkillType(skillType);
        taskRecord.setPrompt(request.getPrompt());
        taskRecord.setWorkspacePath(workspaceDir.toString());
        taskRecord.setCreatedAt(Instant.now());
        taskRecord.setUpdatedAt(taskRecord.getCreatedAt());
        taskRecord.setStatus(TaskStatus.PENDING);
        taskRecord.setMessage("任务已创建，等待执行。");
        tasks.put(taskId, taskRecord);
        persistAndTrace(taskRecord, "SUBMITTED", TaskStatus.PENDING.name(), "任务已创建", simpleDetails("workspacePath", workspaceDir.toString()));

        try {
            transitionStatus(taskRecord, TaskStatus.RUNNING, "任务开始执行。");

            Files.write(workspaceDir.resolve(INPUT_FILE_NAME),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(request));
            appendArtifact(taskRecord, INPUT_FILE_NAME);
            persistAndTrace(taskRecord, "INPUT_PERSISTED", TaskStatus.RUNNING.name(), "输入数据已落盘", simpleDetails("file", INPUT_FILE_NAME));

            SkillBundle skillBundle = skillService.load(skillType);
            persistAndTrace(taskRecord, "SKILL_LOADED", TaskStatus.RUNNING.name(), "Skill 模板已加载", simpleDetails("reference", skillType.getReferenceFile()));

            GitDiffView gitDiffView = tryCollectGitDiff(request, workspaceDir, taskRecord);
            MethodSourceView methodSourceView = tryLookupMethodSource(request, workspaceDir, taskRecord);
            AnalysisExecutionResult analysisExecutionResult = analysisEngine.generate(request, skillType, skillBundle, taskId);
            String artifactContent = analysisExecutionResult.getContent();
            if (gitDiffView != null) {
                artifactContent = artifactContent + "\n\n## 自动补充代码变更\n\n" + formatGitDiffContext(gitDiffView);
            }
            if (methodSourceView != null) {
                artifactContent = artifactContent + "\n\n## 自动补充源码上下文\n\n" + formatSourceContext(methodSourceView);
            }
            Files.write(workspaceDir.resolve(skillType.getArtifactFileName()), artifactContent.getBytes(StandardCharsets.UTF_8));
            appendArtifact(taskRecord, skillType.getArtifactFileName());
            persistAndTrace(taskRecord, "ARTIFACT_WRITTEN", TaskStatus.RUNNING.name(), "结构化结果已生成", buildArtifactDetails(skillType.getArtifactFileName(), analysisExecutionResult));

            taskRecord.setMessage(buildCompletionMessage(methodSourceView != null));
            taskRecord.setStatus(TaskStatus.PARTIAL_SUCCESS);
            taskRecord.setUpdatedAt(Instant.now());
            appendArtifact(taskRecord, TRACE_SUMMARY_FILE_NAME);
            persistAndTrace(taskRecord, "COMPLETED", TaskStatus.PARTIAL_SUCCESS.name(), taskRecord.getMessage(), simpleDetails("artifacts", taskRecord.getArtifacts()));
            tasks.put(taskId, taskRecord);
            return TaskView.from(taskRecord);
        } catch (IOException ex) {
            taskRecord.setLastError(ex.getMessage());
            taskRecord.setMessage("任务执行失败，请检查 trace-summary.json 和 execution-events.jsonl。");
            taskRecord.setStatus(TaskStatus.FAILED);
            taskRecord.setUpdatedAt(Instant.now());
            persistAndTrace(taskRecord, "FAILED", TaskStatus.FAILED.name(), ex.getMessage(), simpleDetails("exception", ex.getClass().getSimpleName()));
            tasks.put(taskId, taskRecord);
            throw ex;
        } catch (RuntimeException ex) {
            taskRecord.setLastError(ex.getMessage());
            taskRecord.setMessage("任务执行失败，请检查 trace-summary.json 和 execution-events.jsonl。");
            taskRecord.setStatus(TaskStatus.FAILED);
            taskRecord.setUpdatedAt(Instant.now());
            persistAndTrace(taskRecord, "FAILED", TaskStatus.FAILED.name(), ex.getMessage(), simpleDetails("exception", ex.getClass().getSimpleName()));
            tasks.put(taskId, taskRecord);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public TaskView getTask(String taskId) {
        TaskRecord taskRecord = findTaskRecord(taskId);
        if (taskRecord == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        return TaskView.from(taskRecord);
    }

    public ArtifactContentView getTaskArtifact(String taskId, String artifactName) throws IOException {
        TaskRecord taskRecord = findTaskRecord(taskId);
        if (taskRecord == null) {
            throw new NoSuchElementException("任务不存在: " + taskId);
        }
        if (!StringUtils.hasText(artifactName)) {
            throw new IllegalArgumentException("artifactName 不能为空");
        }
        if (!taskRecord.getArtifacts().contains(artifactName)) {
            throw new NoSuchElementException("任务产物不存在: " + artifactName);
        }

        Path artifactPath = taskPersistenceService.resolveArtifactPath(taskRecord, artifactName);
        if (!Files.exists(artifactPath)) {
            throw new NoSuchElementException("任务产物不存在: " + artifactName);
        }

        ArtifactContentView artifactContentView = new ArtifactContentView();
        artifactContentView.setTaskId(taskId);
        artifactContentView.setArtifactName(artifactName);
        artifactContentView.setContentType(resolveArtifactContentType(artifactName));
        artifactContentView.setSize(Files.size(artifactPath));
        artifactContentView.setContent(new String(Files.readAllBytes(artifactPath), StandardCharsets.UTF_8));
        return artifactContentView;
    }

    private TaskRecord findTaskRecord(String taskId) {
        TaskRecord taskRecord = tasks.get(taskId);
        if (taskRecord == null) {
            try {
                taskRecord = taskPersistenceService.loadTaskRecordByTaskId(properties.getWorkspaceBaseDir(), taskId);
            } catch (IOException ex) {
                throw new IllegalStateException("读取任务状态失败: " + taskId, ex);
            }
        }
        return taskRecord;
    }

    private String resolveArtifactContentType(String artifactName) {
        String lowered = artifactName.toLowerCase();
        if (lowered.endsWith(".json") || lowered.endsWith(".jsonl")) {
            return "application/json";
        }
        if (lowered.endsWith(".md")) {
            return "text/markdown";
        }
        if (lowered.endsWith(".patch") || lowered.endsWith(".txt")) {
            return "text/plain";
        }
        return "text/plain";
    }

    private void transitionStatus(TaskRecord taskRecord, TaskStatus status, String message) throws IOException {
        taskRecord.setStatus(status);
        taskRecord.setMessage(message);
        taskRecord.setUpdatedAt(Instant.now());
        persistAndTrace(taskRecord, "STATUS_CHANGED", status.name(), message, simpleDetails("status", status.name()));
    }

    private GitDiffView tryCollectGitDiff(CreateTaskRequest request, Path workspaceDir, TaskRecord taskRecord) throws IOException {
        GitDiffQueryRequest queryRequest = buildGitDiffQuery(request);
        if (queryRequest == null) {
            persistAndTrace(taskRecord, "GIT_DIFF_SKIPPED", TaskStatus.RUNNING.name(), "未提供 git diff 查询条件", new LinkedHashMap<>());
            return null;
        }
        GitDiffView gitDiffView = gitDiffService.getGitDiff(queryRequest);
        Files.write(workspaceDir.resolve(GIT_DIFF_FILE_NAME), gitDiffView.getDiffOutput().getBytes(StandardCharsets.UTF_8));
        appendArtifact(taskRecord, GIT_DIFF_FILE_NAME);
        Files.write(workspaceDir.resolve(DIFF_IMPACT_FILE_NAME),
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(gitDiffView));
        appendArtifact(taskRecord, DIFF_IMPACT_FILE_NAME);
        enrichChainDataWithGitDiff(request, gitDiffView);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("changedFiles", gitDiffView.getChangedFiles());
        details.put("relatedInterfaces", gitDiffView.getRelatedInterfaces().size());
        details.put("scenarioCandidates", gitDiffView.getScenarioCandidates().size());
        details.put("truncated", gitDiffView.getTruncated());
        persistAndTrace(taskRecord, "GIT_DIFF_COLLECTED", TaskStatus.RUNNING.name(), "已采集 git diff 代码变更", details);
        return gitDiffView;
    }

    private MethodSourceView tryLookupMethodSource(CreateTaskRequest request, Path workspaceDir, TaskRecord taskRecord) throws IOException {
        MethodSourceQueryRequest queryRequest = buildMethodSourceQuery(request);
        if (queryRequest == null) {
            persistAndTrace(taskRecord, "SOURCE_LOOKUP_SKIPPED", TaskStatus.RUNNING.name(), "未识别到可用的源码补查条件", new LinkedHashMap<>());
            return null;
        }
        try {
            MethodSourceView methodSourceView = methodSourceService.findMethodSource(queryRequest);
            Files.write(workspaceDir.resolve(SOURCE_CONTEXT_FILE_NAME), formatSourceContext(methodSourceView).getBytes(StandardCharsets.UTF_8));
            appendArtifact(taskRecord, SOURCE_CONTEXT_FILE_NAME);
            taskRecord.setSourceLookupApplied(Boolean.TRUE);
            taskRecord.setUpdatedAt(Instant.now());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("filePath", methodSourceView.getFilePath());
            details.put("lineRange", methodSourceView.getStartLine() + "-" + methodSourceView.getEndLine());
            persistAndTrace(taskRecord, "SOURCE_LOOKUP_FOUND", TaskStatus.RUNNING.name(), "已自动补充源码上下文", details);
            return methodSourceView;
        } catch (NoSuchElementException ex) {
            taskRecord.setSourceLookupApplied(Boolean.FALSE);
            taskRecord.setUpdatedAt(Instant.now());
            persistAndTrace(taskRecord, "SOURCE_LOOKUP_MISSED", TaskStatus.RUNNING.name(), ex.getMessage(), simpleDetails("className", queryRequest.getClassName()));
            return null;
        }
    }

    private MethodSourceQueryRequest buildMethodSourceQuery(CreateTaskRequest request) {
        Map<String, Object> options = request.getOptions();
        Map<String, Object> optionQuery = castToStringObjectMap(options == null ? null : options.get("methodSourceQuery"));
        if (!optionQuery.isEmpty()) {
            return toMethodSourceQuery(optionQuery);
        }
        if (request.getChainData() == null) {
            return null;
        }
        Map<String, Object> entry = castToStringObjectMap(request.getChainData().get("entry"));
        if (entry.isEmpty()) {
            return null;
        }
        return toMethodSourceQuery(entry);
    }

    private GitDiffQueryRequest buildGitDiffQuery(CreateTaskRequest request) {
        Map<String, Object> options = request.getOptions();
        Map<String, Object> optionQuery = castToStringObjectMap(options == null ? null : options.get("gitDiffQuery"));
        if (optionQuery.isEmpty()) {
            return null;
        }
        GitDiffQueryRequest queryRequest = new GitDiffQueryRequest();
        queryRequest.setRepositoryPath(stringValue(optionQuery, "repositoryPath", "repository_path"));
        queryRequest.setDiffRange(stringValue(optionQuery, "diffRange", "diff_range"));
        queryRequest.setCached(booleanValue(optionQuery, "cached"));
        Integer maxCharacters = integerValue(optionQuery, "maxCharacters", "max_characters");
        if (maxCharacters != null) {
            queryRequest.setMaxCharacters(maxCharacters);
        }
        List<String> pathspecs = castToStringList(optionQuery.get("pathspecs"));
        if (pathspecs.isEmpty()) {
            pathspecs = castToStringList(optionQuery.get("paths"));
        }
        if (!pathspecs.isEmpty()) {
            queryRequest.setPathspecs(pathspecs);
        }
        return queryRequest;
    }

    private MethodSourceQueryRequest toMethodSourceQuery(Map<String, Object> source) {
        String className = stringValue(source, "class_name", "className");
        String methodName = stringValue(source, "method_name", "methodName");
        if (!StringUtils.hasText(className) || !StringUtils.hasText(methodName)) {
            return null;
        }
        MethodSourceQueryRequest queryRequest = new MethodSourceQueryRequest();
        queryRequest.setClassName(className);
        queryRequest.setMethodName(methodName);
        queryRequest.setFileHint(stringValue(source, "file_hint", "fileHint"));
        queryRequest.setPackageName(stringValue(source, "package_name", "packageName"));
        Integer parameterCount = integerValue(source, "parameter_count", "parameterCount");
        queryRequest.setParameterCount(parameterCount);
        List<String> searchRoots = castToStringList(source.get("search_roots"));
        if (searchRoots.isEmpty()) {
            searchRoots = castToStringList(source.get("searchRoots"));
        }
        if (!searchRoots.isEmpty()) {
            queryRequest.setSearchRoots(searchRoots);
        }
        return queryRequest;
    }

    private String stringValue(Map<String, Object> source, String firstKey, String secondKey) {
        Object value = source.get(firstKey);
        if (value == null) {
            value = source.get(secondKey);
        }
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> castToStringObjectMap(Object value) {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> rawMap = (Map<?, ?>) value;
        Map<String, Object> converted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return converted;
    }

    private List<String> castToStringList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rawList = (List<?>) value;
        List<String> converted = new ArrayList<>();
        for (Object item : rawList) {
            if (item != null) {
                converted.add(String.valueOf(item));
            }
        }
        return converted;
    }

    private Integer integerValue(Map<String, Object> source, String firstKey, String secondKey) {
        Object value = source.get(firstKey);
        if (value == null) {
            value = source.get(secondKey);
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            return Integer.valueOf((String) value);
        }
        return null;
    }

    private Boolean booleanValue(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            return Boolean.valueOf((String) value);
        }
        return Boolean.FALSE;
    }

    private void enrichChainDataWithGitDiff(CreateTaskRequest request, GitDiffView gitDiffView) {
        Map<String, Object> chainData = request.getChainData();
        if (chainData == null) {
            chainData = new LinkedHashMap<>();
            request.setChainData(chainData);
        }
        if (chainData.get("gitDiff") != null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("repositoryPath", gitDiffView.getRepositoryPath());
        payload.put("diffRange", gitDiffView.getDiffRange());
        payload.put("cached", gitDiffView.getCached());
        payload.put("changedFiles", gitDiffView.getChangedFiles());
        payload.put("changedClasses", gitDiffView.getChangedClasses());
        payload.put("changedMethods", gitDiffView.getChangedMethods());
        payload.put("relatedInterfaces", gitDiffView.getRelatedInterfaces());
        payload.put("scenarioCandidates", gitDiffView.getScenarioCandidates());
        payload.put("truncated", gitDiffView.getTruncated());
        payload.put("diffOutput", trimDiffOutputForLlm(gitDiffView.getDiffOutput()));
        payload.put("diffOutputOriginalLength", gitDiffView.getDiffOutput() == null ? 0 : gitDiffView.getDiffOutput().length());
        payload.put("diffOutputTruncatedForLlm", gitDiffView.getDiffOutput() != null
            && gitDiffView.getDiffOutput().length() > MAX_LLM_DIFF_OUTPUT_CHARACTERS);
        chainData.put("gitDiff", payload);
    }

    private String trimDiffOutputForLlm(String diffOutput) {
        if (!StringUtils.hasText(diffOutput) || diffOutput.length() <= MAX_LLM_DIFF_OUTPUT_CHARACTERS) {
            return diffOutput;
        }
        return diffOutput.substring(0, MAX_LLM_DIFF_OUTPUT_CHARACTERS)
            + "\n...\n[diff output truncated for llm context]";
    }

    private String formatSourceContext(MethodSourceView methodSourceView) {
        StringBuilder builder = new StringBuilder();
        builder.append("- 文件路径: ").append(methodSourceView.getFilePath()).append("\n");
        builder.append("- 行号范围: ").append(methodSourceView.getStartLine()).append("-").append(methodSourceView.getEndLine()).append("\n");
        builder.append("- 匹配签名: ").append(methodSourceView.getMatchedSignature()).append("\n\n");
        builder.append("```java\n").append(methodSourceView.getSourceCode()).append("\n```\n");
        return builder.toString();
    }

    private String formatGitDiffContext(GitDiffView gitDiffView) {
        StringBuilder builder = new StringBuilder();
        builder.append("- 仓库路径: ").append(gitDiffView.getRepositoryPath()).append("\n");
        builder.append("- diffRange: ").append(gitDiffView.getDiffRange() == null ? "工作区变更" : gitDiffView.getDiffRange()).append("\n");
        builder.append("- cached: ").append(gitDiffView.getCached()).append("\n");
        builder.append("- changedFiles: ").append(gitDiffView.getChangedFiles()).append("\n");
        builder.append("- changedClasses: ").append(gitDiffView.getChangedClasses()).append("\n");
        builder.append("- changedMethods: ").append(gitDiffView.getChangedMethods()).append("\n");
        builder.append("- relatedInterfaces: ").append(gitDiffView.getRelatedInterfaces().size()).append("\n");
        builder.append("- scenarioCandidates: ").append(gitDiffView.getScenarioCandidates().size()).append("\n");
        builder.append("- truncated: ").append(gitDiffView.getTruncated()).append("\n\n");
        if (!gitDiffView.getRelatedInterfaces().isEmpty()) {
            builder.append("### 关联接口\n\n");
            for (com.apitestagent.web.dto.RelatedInterfaceView relatedInterface : gitDiffView.getRelatedInterfaces()) {
                builder.append("- [").append(relatedInterface.getRelationType()).append("] ")
                    .append(relatedInterface.getHttpMethod()).append(" ")
                    .append(relatedInterface.getPath()).append(" -> ")
                    .append(relatedInterface.getControllerClass()).append("#")
                    .append(relatedInterface.getHandlerMethod()).append("，依据: ")
                    .append(relatedInterface.getEvidence()).append("\n");
            }
            builder.append("\n");
        }
        if (!gitDiffView.getScenarioCandidates().isEmpty()) {
            builder.append("### 场景候选\n\n");
            for (com.apitestagent.web.dto.ScenarioCandidateView scenarioCandidate : gitDiffView.getScenarioCandidates()) {
                builder.append("- [").append(scenarioCandidate.getPriority()).append("] ")
                    .append(scenarioCandidate.getScenarioId()).append(" ")
                    .append(scenarioCandidate.getScenarioName()).append(" -> ")
                    .append(scenarioCandidate.getRelatedInterfaceChain()).append("，业务对象: ")
                    .append(scenarioCandidate.getBusinessObject()).append("，共享主键: ")
                    .append(scenarioCandidate.getSharedKeyHints()).append("，数据传递: ")
                    .append(scenarioCandidate.getDataFlowHint()).append("，状态提示: ")
                        .append(scenarioCandidate.getStateTransitionHint()).append("，字段传递: ")
                        .append(scenarioCandidate.getFieldTransferHints()).append("，请求绑定: ")
                        .append(scenarioCandidate.getRequestBindingHints()).append("，响应字段: ")
                        .append(scenarioCandidate.getResponseFieldHints()).append("，依赖提示: ")
                    .append(scenarioCandidate.getDependencyHints()).append("，触发条件: ")
                    .append(scenarioCandidate.getTriggerCondition()).append("\n");
            }
            builder.append("\n");
        }
        builder.append("```diff\n").append(gitDiffView.getDiffOutput()).append("\n```\n");
        return builder.toString();
    }

    private void appendArtifact(TaskRecord taskRecord, String artifact) {
        List<String> artifacts = taskRecord.getArtifacts();
        if (artifacts == null) {
            artifacts = new ArrayList<>();
            taskRecord.setArtifacts(artifacts);
        }
        if (!artifacts.contains(artifact)) {
            artifacts.add(artifact);
        }
    }

    private void persistAndTrace(TaskRecord taskRecord,
                                 String phase,
                                 String status,
                                 String message,
                                 Map<String, Object> details) throws IOException {
        taskRecord.setUpdatedAt(Instant.now());
        taskPersistenceService.appendExecutionEvent(taskRecord, phase, status, message, details);
        taskPersistenceService.saveTaskRecord(taskRecord);
        taskPersistenceService.writeTraceSummary(taskRecord);
    }

    private Map<String, Object> simpleDetails(String key, Object value) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(key, value);
        return details;
    }

    private Map<String, Object> buildArtifactDetails(String fileName, AnalysisExecutionResult analysisExecutionResult) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("file", fileName);
        details.putAll(analysisExecutionResult.getMetadata());
        return details;
    }

    private String buildCompletionMessage(boolean sourceResolved) {
        if (sourceResolved) {
            return "已生成结构化草稿，并自动补充了源码上下文；待接入真实模型执行引擎。";
        }
        return "已生成结构化草稿；当前未补充源码上下文，待接入真实模型执行引擎。";
    }
}
