package com.apitestagent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.apitestagent.web.dto.GitCommitView;
import com.apitestagent.web.dto.GitDiffQueryRequest;
import com.apitestagent.web.dto.GitDiffView;
import com.apitestagent.web.dto.GitHistoryQueryRequest;
import com.apitestagent.web.dto.GitHistoryView;
import com.apitestagent.web.dto.GitReferenceView;
import com.apitestagent.web.dto.RelatedInterfaceView;
import com.apitestagent.web.dto.ScenarioCandidateView;

@Service
public class GitDiffService {

    private static final String JAVA_EXTENSION = ".java";
    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+(\\w+)");
    private static final Pattern REQUEST_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\((.*)\\)");
    private static final Pattern QUOTED_PATH_PATTERN = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.(GET|POST|PUT|DELETE|PATCH)");
    private static final String FIELD_SEPARATOR = "\u001F";
    private static final String LINE_SPLIT_REGEX = "\\r?\\n";
    private static final String HTTP_PATCH = "PATCH";
    private static final String HTTP_DELETE = "DELETE";
    private static final String RELATION_DIRECT = "DIRECT";
    private static final String CONTROLLER_SUFFIX = "Controller";
    private static final String DEP_REPOSITORY = "数据库/Repository";
    private static final String DEP_CACHE = "缓存/Redis";
    private static final String DEP_HTTP = "外部HTTP调用";
    private static final String DEP_MESSAGE = "消息/异步事件";
    private static final Pattern PATH_VARIABLE_PATTERN = Pattern.compile("\\{([^/}]+)\\}");
    private static final Pattern ID_TOKEN_PATTERN = Pattern.compile("\\b([A-Za-z][A-Za-z0-9]*Id)\\b");

    public GitHistoryView getGitHistory(GitHistoryQueryRequest request) throws IOException {
        GitHistoryQueryRequest effectiveRequest = request == null ? new GitHistoryQueryRequest() : request;
        Path repositoryPath = resolveRepositoryPath(effectiveRequest.getRepositoryPath());
        validateRepositoryPath(repositoryPath);
        int pageNumber = resolvePageNumber(effectiveRequest.getPageNumber());
        int pageSize = resolvePageSize(effectiveRequest.getMaxCount());
        String searchQuery = normalizeSearchQuery(effectiveRequest.getSearchQuery());

        GitHistoryView view = new GitHistoryView();
        view.setRepositoryPath(repositoryPath.toString());
        view.setCurrentBranch(resolveCurrentBranch(repositoryPath));
        view.setResolvedRef(resolveHistoryRef(effectiveRequest, view.getCurrentBranch()));
        view.setSearchQuery(searchQuery);
        view.setPageNumber(pageNumber);
        view.setPageSize(pageSize);
        view.setRefs(readReferences(repositoryPath));
        CommitPage commitPage = readCommits(repositoryPath, view.getResolvedRef(), searchQuery, pageNumber, pageSize);
        view.setCommits(commitPage.commits);
        view.setTotalCount(commitPage.totalCount);
        view.setHasNextPage(commitPage.hasNextPage);
        return view;
    }

    public GitDiffView getGitDiff(GitDiffQueryRequest request) throws IOException {
        GitDiffQueryRequest effectiveRequest = request == null ? new GitDiffQueryRequest() : request;
        Path repositoryPath = resolveRepositoryPath(effectiveRequest.getRepositoryPath());
        validateRepositoryPath(repositoryPath);

        List<String> changedFiles = readLines(repositoryPath, buildNameOnlyCommand(effectiveRequest));
        String diffOutput = readText(repositoryPath, buildDiffCommand(effectiveRequest));

        GitDiffView view = new GitDiffView();
        view.setRepositoryPath(repositoryPath.toString());
        view.setDiffRange(effectiveRequest.getDiffRange());
        view.setCached(Boolean.TRUE.equals(effectiveRequest.getCached()));
        view.setChangedFiles(changedFiles);
        applyDiffOutput(view, diffOutput, effectiveRequest.getMaxCharacters());
        // Keep impact analysis close to raw diff extraction so A4 can consume one structured payload.
        view.setChangedClasses(extractChangedClasses(changedFiles));
        view.setChangedMethods(extractChangedMethods(diffOutput));
        view.setRelatedInterfaces(findRelatedInterfaces(repositoryPath, changedFiles, view.getChangedClasses(), view.getChangedMethods()));
        view.setScenarioCandidates(findScenarioCandidates(repositoryPath, changedFiles, view.getChangedClasses(), view.getChangedMethods()));
        return view;
    }

    private List<String> extractChangedClasses(List<String> changedFiles) {
        Set<String> changedClasses = new LinkedHashSet<>();
        for (String changedFile : changedFiles) {
            if (StringUtils.hasText(changedFile) && changedFile.endsWith(JAVA_EXTENSION)) {
                Path fileName = Paths.get(changedFile).getFileName();
                if (fileName != null) {
                    String simpleName = fileName.toString();
                    changedClasses.add(simpleName.substring(0, simpleName.length() - JAVA_EXTENSION.length()));
                }
            }
        }
        return new ArrayList<>(changedClasses);
    }

    private List<String> extractChangedMethods(String diffOutput) {
        if (!StringUtils.hasText(diffOutput)) {
            return Collections.emptyList();
        }
        Set<String> methods = new LinkedHashSet<>();
        String[] lines = splitLines(diffOutput);
        String currentMethod = null;
        for (String line : lines) {
            String normalizedLine = normalizeDiffLine(line);
            String detectedMethod = extractMethodName(normalizedLine);
            if (StringUtils.hasText(detectedMethod)) {
                currentMethod = detectedMethod;
            }
            String candidate = toChangedCodeLine(line);
            if (candidate != null) {
                String methodName = extractMethodName(candidate);
                if (StringUtils.hasText(methodName) && !isControlKeyword(methodName)) {
                    methods.add(methodName);
                } else if (StringUtils.hasText(currentMethod)) {
                    methods.add(currentMethod);
                }
            }
        }
        return new ArrayList<>(methods);
    }

    private String normalizeDiffLine(String line) {
        if (!StringUtils.hasText(line)) {
            return "";
        }
        if (line.startsWith("+++") || line.startsWith("---") || line.startsWith("@@")) {
            return "";
        }
        if (line.startsWith("+") || line.startsWith("-") || line.startsWith(" ")) {
            return line.substring(1).trim();
        }
        return line.trim();
    }

    private String toChangedCodeLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        boolean diffMarker = line.startsWith("+") || line.startsWith("-");
        boolean header = line.startsWith("+++") || line.startsWith("---");
        if (!diffMarker || header) {
            return null;
        }
        return line.substring(1).trim();
    }

    private boolean isControlKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return "if".equals(normalized) || "for".equals(normalized) || "while".equals(normalized)
            || "switch".equals(normalized) || "catch".equals(normalized) || "return".equals(normalized);
    }

    private List<RelatedInterfaceView> findRelatedInterfaces(Path repositoryPath,
                                                             List<String> changedFiles,
                                                             List<String> changedClasses,
                                                             List<String> changedMethods) throws IOException {
        Path javaRoot = repositoryPath.resolve(Paths.get("src", "main", "java"));
        if (!Files.exists(javaRoot)) {
            return Collections.emptyList();
        }
        List<Path> controllerFiles = collectControllerFiles(javaRoot);
        List<RelatedInterfaceView> result = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        for (Path controllerFile : controllerFiles) {
            ControllerDescriptor descriptor = parseController(repositoryPath, controllerFile);
            if (descriptor != null && !descriptor.mappings.isEmpty()) {
                String relationType = resolveRelationType(descriptor, changedFiles, changedClasses, changedMethods);
                if (relationType != null) {
                    String evidence = buildEvidence(descriptor, changedFiles, changedClasses, changedMethods);
                    addRelatedMappings(result, dedupe, descriptor, relationType, evidence);
                }
            }
        }
        return result;
    }

    private List<ScenarioCandidateView> findScenarioCandidates(Path repositoryPath,
                                                               List<String> changedFiles,
                                                               List<String> changedClasses,
                                                               List<String> changedMethods) throws IOException {
        Path javaRoot = repositoryPath.resolve(Paths.get("src", "main", "java"));
        if (!Files.exists(javaRoot)) {
            return Collections.emptyList();
        }
        List<Path> controllerFiles = collectControllerFiles(javaRoot);
        List<ScenarioCandidateView> result = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        int index = 1;
        for (Path controllerFile : controllerFiles) {
            ControllerDescriptor descriptor = parseController(repositoryPath, controllerFile);
            if (descriptor != null && !descriptor.mappings.isEmpty()) {
                String relationType = resolveRelationType(descriptor, changedFiles, changedClasses, changedMethods);
                if (StringUtils.hasText(relationType)) {
                    List<ControllerMapping> orderedMappings = orderMappings(descriptor.mappings);
                    String dedupeKey = descriptor.controllerClass + "|" + buildChainKey(orderedMappings);
                    if (dedupe.add(dedupeKey)) {
                        ScenarioCandidateView candidate = new ScenarioCandidateView();
                        candidate.setScenarioId(String.format(Locale.ROOT, "SCN-%03d", index++));
                        candidate.setScenarioType(resolveScenarioType(orderedMappings, relationType));
                        candidate.setScenarioName(buildScenarioName(descriptor, orderedMappings, candidate.getScenarioType()));
                        candidate.setEntryInterface(formatMapping(selectEntryMapping(orderedMappings)));
                        candidate.setRelatedInterfaceChain(toInterfaceChain(orderedMappings));
                        candidate.setTriggerCondition(buildTriggerCondition(changedClasses, changedMethods, orderedMappings));
                        candidate.setBusinessObject(inferBusinessObject(descriptor, orderedMappings));
                        candidate.setSharedKeyHints(inferSharedKeyHints(descriptor, orderedMappings));
                        candidate.setResponseFieldHints(inferResponseFieldHints(orderedMappings, candidate.getSharedKeyHints()));
                        candidate.setRequestBindingHints(inferRequestBindingHints(orderedMappings, candidate.getSharedKeyHints()));
                        candidate.setFieldTransferHints(inferFieldTransferHints(candidate.getResponseFieldHints(), candidate.getRequestBindingHints()));
                        candidate.setDataFlowHint(inferDataFlowHint(orderedMappings, candidate.getSharedKeyHints()));
                        candidate.setStateTransitionHint(inferStateTransitionHint(orderedMappings));
                        candidate.setDependencyHints(inferDependencyHints(descriptor));
                        candidate.setEvidence(buildScenarioEvidence(descriptor, changedFiles, changedClasses, changedMethods, orderedMappings));
                        candidate.setPriority(resolveScenarioPriority(relationType, orderedMappings));
                        result.add(candidate);
                    }
                }
            }
        }
        return result;
    }

    private void addRelatedMappings(List<RelatedInterfaceView> result,
                                    Set<String> dedupe,
                                    ControllerDescriptor descriptor,
                                    String relationType,
                                    String evidence) {
        for (ControllerMapping mapping : descriptor.mappings) {
            String key = mapping.httpMethod + "|" + mapping.path + "|" + mapping.handlerMethod;
            if (dedupe.add(key)) {
                RelatedInterfaceView view = new RelatedInterfaceView();
                view.setControllerClass(descriptor.controllerClass);
                view.setHandlerMethod(mapping.handlerMethod);
                view.setHttpMethod(mapping.httpMethod);
                view.setPath(mapping.path);
                view.setRelationType(relationType);
                view.setEvidence(evidence);
                view.setSourceFile(descriptor.relativePath);
                result.add(view);
            }
        }
    }

    private List<ControllerMapping> orderMappings(List<ControllerMapping> mappings) {
        List<ControllerMapping> ordered = new ArrayList<>(mappings);
        Collections.sort(ordered, (left, right) -> {
            int scoreCompare = Integer.compare(mappingOrderScore(left), mappingOrderScore(right));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return formatMapping(left).compareTo(formatMapping(right));
        });
        return ordered;
    }

    private int mappingOrderScore(ControllerMapping mapping) {
        if (mapping == null || !StringUtils.hasText(mapping.httpMethod)) {
            return 99;
        }
        String method = mapping.httpMethod.toUpperCase(Locale.ROOT);
        if ("POST".equals(method)) {
            return 1;
        }
        if ("PUT".equals(method) || HTTP_PATCH.equals(method)) {
            return 2;
        }
        if ("GET".equals(method)) {
            return 3;
        }
        if (HTTP_DELETE.equals(method)) {
            return 4;
        }
        return 9;
    }

    private String buildChainKey(List<ControllerMapping> mappings) {
        StringBuilder builder = new StringBuilder();
        for (ControllerMapping mapping : mappings) {
            if (builder.length() > 0) {
                builder.append(" -> ");
            }
            builder.append(formatMapping(mapping));
        }
        return builder.toString();
    }

    private String resolveScenarioType(List<ControllerMapping> mappings, String relationType) {
        boolean hasRead = hasMethod(mappings, "GET");
        boolean hasCreate = hasMethod(mappings, "POST");
        boolean hasUpdate = hasMethod(mappings, "PUT") || hasMethod(mappings, HTTP_PATCH);
        boolean hasDelete = hasMethod(mappings, HTTP_DELETE);
        if (hasCreate && hasRead) {
            return "主流程/状态流转";
        }
        if ((hasUpdate || hasDelete) && hasRead) {
            return "状态流转/逆向流程";
        }
        if (mappings.size() > 1) {
            return "多接口回归链";
        }
        if (RELATION_DIRECT.equalsIgnoreCase(relationType)) {
            return "直接变更场景";
        }
        return "关联回归场景";
    }

    private boolean hasMethod(List<ControllerMapping> mappings, String httpMethod) {
        for (ControllerMapping mapping : mappings) {
            if (mapping != null && httpMethod.equalsIgnoreCase(mapping.httpMethod)) {
                return true;
            }
        }
        return false;
    }

    private String buildScenarioName(ControllerDescriptor descriptor,
                                     List<ControllerMapping> mappings,
                                     String scenarioType) {
        String base = descriptor.controllerClass;
        if (StringUtils.hasText(base) && base.endsWith(CONTROLLER_SUFFIX)) {
            base = base.substring(0, base.length() - CONTROLLER_SUFFIX.length());
        }
        if (scenarioType.contains("主流程")) {
            return base + "写后读验证场景";
        }
        if (scenarioType.contains("逆向流程")) {
            return base + "状态回退场景";
        }
        if (mappings.size() > 1) {
            return base + "多接口联动场景";
        }
        return base + "接口变更验证场景";
    }

    private ControllerMapping selectEntryMapping(List<ControllerMapping> mappings) {
        return mappings.isEmpty() ? null : mappings.get(0);
    }

    private List<String> toInterfaceChain(List<ControllerMapping> mappings) {
        List<String> chain = new ArrayList<>();
        for (ControllerMapping mapping : mappings) {
            chain.add(formatMapping(mapping));
        }
        return chain;
    }

    private String buildTriggerCondition(List<String> changedClasses,
                                         List<String> changedMethods,
                                         List<ControllerMapping> mappings) {
        List<String> parts = new ArrayList<>();
        if (!changedMethods.isEmpty()) {
            parts.add("受变更方法影响: " + joinNames(changedMethods, 3));
        }
        if (!changedClasses.isEmpty()) {
            parts.add("受变更类影响: " + joinNames(changedClasses, 2));
        }
        if (mappings.size() > 1) {
            parts.add("需要串联校验同一 Controller 下的接口链");
        }
        return parts.isEmpty() ? "需基于本次 diff 补充场景验证" : joinEvidence(parts);
    }

    private String joinNames(List<String> values, int maxItems) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(values.size(), maxItems);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(", ");
            }
            builder.append(values.get(index));
        }
        if (values.size() > limit) {
            builder.append(" 等");
        }
        return builder.toString();
    }

    private String buildScenarioEvidence(ControllerDescriptor descriptor,
                                         List<String> changedFiles,
                                         List<String> changedClasses,
                                         List<String> changedMethods,
                                         List<ControllerMapping> mappings) {
        List<String> evidenceParts = new ArrayList<>();
        evidenceParts.add(buildEvidence(descriptor, changedFiles, changedClasses, changedMethods));
        String businessObject = inferBusinessObject(descriptor, mappings);
        if (StringUtils.hasText(businessObject)) {
            evidenceParts.add("共享业务对象: " + businessObject);
        }
        List<String> sharedKeyHints = inferSharedKeyHints(descriptor, mappings);
        if (!sharedKeyHints.isEmpty()) {
            evidenceParts.add("共享主键: " + sharedKeyHints);
        }
        List<String> responseFieldHints = inferResponseFieldHints(mappings, sharedKeyHints);
        if (!responseFieldHints.isEmpty()) {
            evidenceParts.add("响应字段: " + responseFieldHints);
        }
        List<String> requestBindingHints = inferRequestBindingHints(mappings, sharedKeyHints);
        if (!requestBindingHints.isEmpty()) {
            evidenceParts.add("请求绑定: " + requestBindingHints);
        }
        List<String> fieldTransferHints = inferFieldTransferHints(responseFieldHints, requestBindingHints);
        if (!fieldTransferHints.isEmpty()) {
            evidenceParts.add("字段传递: " + fieldTransferHints);
        }
        String dataFlowHint = inferDataFlowHint(mappings, sharedKeyHints);
        if (StringUtils.hasText(dataFlowHint)) {
            evidenceParts.add("数据传递: " + dataFlowHint);
        }
        String stateTransitionHint = inferStateTransitionHint(mappings);
        if (StringUtils.hasText(stateTransitionHint)) {
            evidenceParts.add("状态提示: " + stateTransitionHint);
        }
        List<String> dependencyHints = inferDependencyHints(descriptor);
        if (!dependencyHints.isEmpty()) {
            evidenceParts.add("依赖提示: " + dependencyHints);
        }
        if (mappings.size() > 1) {
            evidenceParts.add("同 Controller 接口链: " + buildChainKey(mappings));
        } else if (!mappings.isEmpty()) {
            evidenceParts.add("入口接口: " + formatMapping(mappings.get(0)));
        }
        return joinEvidence(evidenceParts);
    }

    private String inferBusinessObject(ControllerDescriptor descriptor, List<ControllerMapping> mappings) {
        if (!mappings.isEmpty()) {
            String path = mappings.get(0).path;
            if (StringUtils.hasText(path)) {
                String normalized = path.replaceAll("\\{[^/]+\\}", "")
                    .replaceAll("^/+", "")
                    .replaceAll("/+$", "");
                if (StringUtils.hasText(normalized)) {
                    String[] segments = normalized.split("/");
                    if (segments.length > 0 && StringUtils.hasText(segments[0])) {
                        return segments[0];
                    }
                }
            }
        }
        String controllerClass = descriptor.controllerClass;
        if (StringUtils.hasText(controllerClass) && controllerClass.endsWith(CONTROLLER_SUFFIX)) {
            return controllerClass.substring(0, controllerClass.length() - CONTROLLER_SUFFIX.length());
        }
        return controllerClass;
    }

    private String inferStateTransitionHint(List<ControllerMapping> mappings) {
        boolean hasCreate = hasMethod(mappings, "POST");
        boolean hasRead = hasMethod(mappings, "GET");
        boolean hasUpdate = hasMethod(mappings, "PUT") || hasMethod(mappings, HTTP_PATCH);
        boolean hasDelete = hasMethod(mappings, HTTP_DELETE);
        if (hasCreate && hasRead && hasUpdate) {
            return "创建后查询并更新状态";
        }
        if (hasCreate && hasRead) {
            return "创建后查询状态";
        }
        if (hasUpdate && hasRead) {
            return "更新后查询状态";
        }
        if (hasDelete && hasRead) {
            return "删除或取消后校验结果";
        }
        if (hasUpdate || hasDelete) {
            return "存在状态变更动作，需补状态流转校验";
        }
        return "场景链待确认";
    }

    private List<String> inferSharedKeyHints(ControllerDescriptor descriptor, List<ControllerMapping> mappings) {
        Set<String> keys = new LinkedHashSet<>();
        for (ControllerMapping mapping : mappings) {
            if (mapping != null && StringUtils.hasText(mapping.path)) {
                Matcher matcher = PATH_VARIABLE_PATTERN.matcher(mapping.path);
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        String sourceText = descriptor.sourceText == null ? "" : descriptor.sourceText;
        Matcher idMatcher = ID_TOKEN_PATTERN.matcher(sourceText);
        while (idMatcher.find()) {
            keys.add(idMatcher.group(1));
        }
        if (keys.isEmpty() && hasMethod(mappings, "GET") && (hasMethod(mappings, "POST") || hasMethod(mappings, "PUT") || hasMethod(mappings, HTTP_PATCH))) {
            keys.add("id");
        }
        return new ArrayList<>(keys);
    }

    private String inferDataFlowHint(List<ControllerMapping> mappings, List<String> sharedKeyHints) {
        String sharedKeys = sharedKeyHints.isEmpty() ? "主键" : joinNames(sharedKeyHints, 2);
        boolean hasCreate = hasMethod(mappings, "POST");
        boolean hasRead = hasMethod(mappings, "GET");
        boolean hasUpdate = hasMethod(mappings, "PUT") || hasMethod(mappings, HTTP_PATCH);
        boolean hasDelete = hasMethod(mappings, HTTP_DELETE);
        if (hasCreate && hasRead) {
            return "使用创建接口返回的 " + sharedKeys + " 驱动后续查询或校验";
        }
        if (hasUpdate && hasRead) {
            return "使用更新接口输入的 " + sharedKeys + " 回查更新后的结果";
        }
        if (hasDelete && hasRead) {
            return "使用删除或取消动作中的 " + sharedKeys + " 校验最终结果";
        }
        if (!sharedKeyHints.isEmpty()) {
            return "相关接口之间通过 " + sharedKeys + " 传递业务对象";
        }
        return "数据传递链待确认";
    }

    private List<String> inferResponseFieldHints(List<ControllerMapping> mappings, List<String> sharedKeyHints) {
        List<String> hints = new ArrayList<>();
        ControllerMapping upstream = selectWriteMapping(mappings);
        if (upstream == null || sharedKeyHints.isEmpty()) {
            return hints;
        }
        for (String sharedKeyHint : sharedKeyHints) {
            hints.add("response." + normalizeFieldToken(sharedKeyHint));
        }
        return hints;
    }

    private List<String> inferRequestBindingHints(List<ControllerMapping> mappings, List<String> sharedKeyHints) {
        List<String> hints = new ArrayList<>();
        if (sharedKeyHints.isEmpty() || mappings.size() <= 1) {
            return hints;
        }
        for (int index = 1; index < mappings.size(); index++) {
            ControllerMapping mapping = mappings.get(index);
            for (String sharedKeyHint : sharedKeyHints) {
                String bindingLocation = resolveBindingLocation(mapping, sharedKeyHint);
                hints.add(formatMapping(mapping) + " -> " + bindingLocation);
            }
        }
        return hints;
    }

    private List<String> inferFieldTransferHints(List<String> responseFieldHints, List<String> requestBindingHints) {
        List<String> hints = new ArrayList<>();
        if (responseFieldHints.isEmpty() || requestBindingHints.isEmpty()) {
            return hints;
        }
        for (String responseFieldHint : responseFieldHints) {
            String fieldName = responseFieldHint.substring(responseFieldHint.lastIndexOf('.') + 1);
            for (String requestBindingHint : requestBindingHints) {
                if (requestBindingHint.toLowerCase(Locale.ROOT).contains(fieldName.toLowerCase(Locale.ROOT))) {
                    hints.add(responseFieldHint + " -> " + requestBindingHint);
                }
            }
        }
        return hints;
    }

    private ControllerMapping selectWriteMapping(List<ControllerMapping> mappings) {
        for (ControllerMapping mapping : mappings) {
            if (mapping != null && ("POST".equalsIgnoreCase(mapping.httpMethod)
                || "PUT".equalsIgnoreCase(mapping.httpMethod)
                || HTTP_PATCH.equalsIgnoreCase(mapping.httpMethod))) {
                return mapping;
            }
        }
        return mappings.isEmpty() ? null : mappings.get(0);
    }

    private String resolveBindingLocation(ControllerMapping mapping, String sharedKeyHint) {
        String normalizedKey = normalizeFieldToken(sharedKeyHint);
        if (mapping != null && StringUtils.hasText(mapping.path)) {
            Matcher matcher = PATH_VARIABLE_PATTERN.matcher(mapping.path);
            while (matcher.find()) {
                String variable = matcher.group(1);
                if (normalizedKey.equalsIgnoreCase(normalizeFieldToken(variable))) {
                    return "path." + variable;
                }
            }
        }
        return "request." + normalizedKey;
    }

    private String normalizeFieldToken(String value) {
        if (!StringUtils.hasText(value)) {
            return "id";
        }
        return value.replaceAll("^[^A-Za-z]+", "").replaceAll("[^A-Za-z0-9]+$", "");
    }

    private List<String> inferDependencyHints(ControllerDescriptor descriptor) {
        List<String> hints = new ArrayList<>();
        String sourceText = descriptor.sourceText == null ? "" : descriptor.sourceText;
        String loweredSource = sourceText.toLowerCase(Locale.ROOT);
        if (loweredSource.contains("repository") || loweredSource.contains("mapper") || loweredSource.contains("dao")) {
            hints.add(DEP_REPOSITORY);
        }
        if (loweredSource.contains("redis") || loweredSource.contains("cache")) {
            hints.add(DEP_CACHE);
        }
        if (loweredSource.contains("resttemplate") || loweredSource.contains("feign") || loweredSource.contains("webclient")
            || loweredSource.contains("httpclient")) {
            hints.add(DEP_HTTP);
        }
        if (loweredSource.contains("kafka") || loweredSource.contains("rabbit") || loweredSource.contains("rocketmq")
            || loweredSource.contains("streambridge") || loweredSource.contains("publish") || loweredSource.contains("message")) {
            hints.add(DEP_MESSAGE);
        }
        return hints;
    }

    private String resolveScenarioPriority(String relationType, List<ControllerMapping> mappings) {
        if (RELATION_DIRECT.equalsIgnoreCase(relationType)) {
            return "P0";
        }
        if (mappings.size() > 1 || hasMethod(mappings, "POST") && hasMethod(mappings, "GET")) {
            return "P1";
        }
        return "P2";
    }

    private String formatMapping(ControllerMapping mapping) {
        if (mapping == null) {
            return "待补充";
        }
        return mapping.httpMethod + " " + mapping.path;
    }

    private List<Path> collectControllerFiles(Path javaRoot) throws IOException {
        final List<Path> controllerFiles = new ArrayList<>();
        Files.walkFileTree(javaRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith("Controller.java")) {
                    controllerFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return controllerFiles;
    }

    private ControllerDescriptor parseController(Path repositoryPath, Path controllerFile) throws IOException {
        List<String> lines = Files.readAllLines(controllerFile, StandardCharsets.UTF_8);
        ClassDeclaration classDeclaration = findClassDeclaration(lines);
        if (classDeclaration == null) {
            return null;
        }

        ControllerDescriptor descriptor = new ControllerDescriptor();
        descriptor.controllerClass = classDeclaration.className;
        descriptor.relativePath = normalize(repositoryPath.relativize(controllerFile).toString());
        descriptor.sourceText = joinLines(lines);
        parseControllerMappings(lines, classDeclaration, descriptor);
        return descriptor;
    }

    private ClassDeclaration findClassDeclaration(List<String> lines) {
        String classPath = "";
        for (int index = 0; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith("@RequestMapping")) {
                ControllerMapping classMapping = parseMapping(trimmed);
                if (classMapping != null) {
                    classPath = classMapping.path;
                }
            } else {
                Matcher classMatcher = CLASS_PATTERN.matcher(trimmed);
                if (classMatcher.find()) {
                    ClassDeclaration declaration = new ClassDeclaration();
                    declaration.classIndex = index;
                    declaration.className = classMatcher.group(1);
                    declaration.classPath = classPath;
                    return declaration;
                }
            }
        }
        return null;
    }

    private void parseControllerMappings(List<String> lines, ClassDeclaration classDeclaration, ControllerDescriptor descriptor) {
        List<ControllerMapping> pendingMappings = new ArrayList<>();
        for (int index = classDeclaration.classIndex + 1; index < lines.size(); index++) {
            String trimmed = lines.get(index).trim();
            ControllerMapping mapping = parseMapping(trimmed);
            if (mapping != null) {
                pendingMappings.add(mapping);
            } else {
                String handlerMethod = extractMethodName(trimmed);
                if (StringUtils.hasText(handlerMethod) && !pendingMappings.isEmpty()) {
                    bindMappings(descriptor, pendingMappings, classDeclaration.classPath, handlerMethod);
                    pendingMappings.clear();
                }
            }
        }
    }

    private void bindMappings(ControllerDescriptor descriptor,
                              List<ControllerMapping> pendingMappings,
                              String classPath,
                              String handlerMethod) {
        for (ControllerMapping pendingMapping : pendingMappings) {
            ControllerMapping resolved = new ControllerMapping();
            resolved.httpMethod = pendingMapping.httpMethod;
            resolved.path = combinePaths(classPath, pendingMapping.path);
            resolved.handlerMethod = handlerMethod;
            descriptor.mappings.add(resolved);
        }
    }

    private ControllerMapping parseMapping(String line) {
        if (!StringUtils.hasText(line) || !line.startsWith("@")) {
            return null;
        }
        ControllerMapping mapping = new ControllerMapping();
        if (line.startsWith("@GetMapping")) {
            mapping.httpMethod = "GET";
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        if (line.startsWith("@PostMapping")) {
            mapping.httpMethod = "POST";
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        if (line.startsWith("@PutMapping")) {
            mapping.httpMethod = "PUT";
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        if (line.startsWith("@DeleteMapping")) {
            mapping.httpMethod = HTTP_DELETE;
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        if (line.startsWith("@PatchMapping")) {
            mapping.httpMethod = HTTP_PATCH;
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        Matcher matcher = REQUEST_MAPPING_PATTERN.matcher(line);
        if (matcher.find()) {
            mapping.httpMethod = extractRequestMethod(matcher.group(1));
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        return null;
    }

    private String extractRequestMethod(String value) {
        Matcher matcher = REQUEST_METHOD_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "REQUEST";
    }

    private String extractQuotedPath(String line) {
        Matcher matcher = QUOTED_PATH_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractMethodName(String line) {
        String trimmed = line == null ? "" : line.trim();
        if (!StringUtils.hasText(trimmed) || trimmed.startsWith("@") || !trimmed.contains("(")) {
            return null;
        }
        int openParenthesis = trimmed.indexOf('(');
        String prefix = trimmed.substring(0, openParenthesis).trim();
        if (!StringUtils.hasText(prefix)) {
            return null;
        }
        String[] tokens = prefix.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }
        String candidate = tokens[tokens.length - 1];
        if (!candidate.matches("[A-Za-z_]\\w*") || isControlKeyword(candidate)) {
            return null;
        }
        if (prefix.startsWith("return ") || prefix.startsWith("new ")) {
            return null;
        }
        return candidate;
    }

    private String combinePaths(String classPath, String methodPath) {
        String left = StringUtils.hasText(classPath) ? classPath.trim() : "";
        String right = StringUtils.hasText(methodPath) ? methodPath.trim() : "";
        if (!left.startsWith("/") && StringUtils.hasText(left)) {
            left = "/" + left;
        }
        if (!right.startsWith("/") && StringUtils.hasText(right)) {
            right = "/" + right;
        }
        String combined = left + right;
        if (!StringUtils.hasText(combined)) {
            return "/";
        }
        return combined.replaceAll("//+", "/");
    }

    private String resolveRelationType(ControllerDescriptor descriptor,
                                       List<String> changedFiles,
                                       List<String> changedClasses,
                                       List<String> changedMethods) {
        if (changedFiles.contains(descriptor.relativePath)) {
            return RELATION_DIRECT;
        }
        for (String changedClass : changedClasses) {
            if (descriptor.sourceText.contains(changedClass)) {
                return "INDIRECT";
            }
        }
        for (String changedMethod : changedMethods) {
            if (descriptor.sourceText.contains(changedMethod + "(")) {
                return "INDIRECT";
            }
        }
        return null;
    }

    private String buildEvidence(ControllerDescriptor descriptor,
                                 List<String> changedFiles,
                                 List<String> changedClasses,
                                 List<String> changedMethods) {
        List<String> evidenceParts = new ArrayList<>();
        if (changedFiles.contains(descriptor.relativePath)) {
            evidenceParts.add("Controller 文件直接变更");
        }
        for (String changedClass : changedClasses) {
            if (descriptor.sourceText.contains(changedClass)) {
                evidenceParts.add("引用变更类: " + changedClass);
            }
        }
        for (String changedMethod : changedMethods) {
            if (descriptor.sourceText.contains(changedMethod + "(")) {
                evidenceParts.add("引用变更方法: " + changedMethod + "()");
            }
        }
        return evidenceParts.isEmpty() ? "未识别到明确证据" : joinEvidence(evidenceParts);
    }

    private String joinEvidence(List<String> evidenceParts) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < evidenceParts.size(); index++) {
            if (index > 0) {
                builder.append("; ");
            }
            builder.append(evidenceParts.get(index));
        }
        return builder.toString();
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value.replace('\\', '/');
    }

    private Path resolveRepositoryPath(String repositoryPath) {
        if (StringUtils.hasText(repositoryPath)) {
            return Paths.get(repositoryPath).toAbsolutePath().normalize();
        }
        return Paths.get("").toAbsolutePath().normalize();
    }

    private void validateRepositoryPath(Path repositoryPath) {
        if (!Files.exists(repositoryPath)) {
            throw new IllegalArgumentException("repositoryPath 不存在: " + repositoryPath);
        }
        if (!Files.exists(repositoryPath.resolve(".git"))) {
            throw new IllegalArgumentException("指定目录不是 git 仓库: " + repositoryPath);
        }
    }

    private List<String> buildNameOnlyCommand(GitDiffQueryRequest request) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add("--name-only");
        command.add("--no-ext-diff");
        applyDiffOptions(command, request);
        return command;
    }

    private List<String> buildDiffCommand(GitDiffQueryRequest request) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        command.add("--no-color");
        command.add("--no-ext-diff");
        applyDiffOptions(command, request);
        return command;
    }

    private String resolveCurrentBranch(Path repositoryPath) throws IOException {
        String output = execute(repositoryPath, createCommand("git", "rev-parse", "--abbrev-ref", "HEAD"), "git 当前分支查询");
        String currentBranch = firstNonBlankLine(output);
        return StringUtils.hasText(currentBranch) ? currentBranch : "HEAD";
    }

    private String resolveHistoryRef(GitHistoryQueryRequest request, String currentBranch) {
        if (StringUtils.hasText(request.getRef())) {
            return request.getRef().trim();
        }
        return StringUtils.hasText(currentBranch) ? currentBranch : "HEAD";
    }

    private List<GitReferenceView> readReferences(Path repositoryPath) throws IOException {
        String output = execute(repositoryPath,
            createCommand(
                "git",
                "for-each-ref",
                "--sort=-committerdate",
                "--format=%(refname:short)" + FIELD_SEPARATOR + "%(refname)" + FIELD_SEPARATOR + "%(objectname:short)" + FIELD_SEPARATOR + "%(committerdate:iso-strict)",
                "refs/heads",
                "refs/tags"),
            "git 引用列表查询");
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }

        List<GitReferenceView> refs = new ArrayList<>();
        for (String line : splitLines(output)) {
            if (StringUtils.hasText(line)) {
                String[] parts = line.split(FIELD_SEPARATOR, -1);
                if (parts.length >= 4) {
                    GitReferenceView view = new GitReferenceView();
                    view.setName(parts[0]);
                    view.setFullName(parts[1]);
                    view.setType(parts[1].startsWith("refs/tags/") ? "TAG" : "BRANCH");
                    view.setTarget(parts[2]);
                    view.setUpdatedAt(parts[3]);
                    refs.add(view);
                }
            }
        }
        return refs;
    }

    private CommitPage readCommits(Path repositoryPath,
                                   String ref,
                                   String searchQuery,
                                   int pageNumber,
                                   int pageSize) throws IOException {
        if (!StringUtils.hasText(searchQuery)) {
            return readCommitPage(repositoryPath, ref, pageNumber, pageSize);
        }
        return searchCommitPage(repositoryPath, ref, searchQuery, pageNumber, pageSize);
    }

    private CommitPage readCommitPage(Path repositoryPath, String ref, int pageNumber, int pageSize) throws IOException {
        int totalCount = readCommitCount(repositoryPath, ref);
        int offset = pageNumber * pageSize;
        if (offset >= totalCount) {
            return new CommitPage(Collections.<GitCommitView>emptyList(), totalCount, false);
        }

        List<String> command = createCommand(
            "git",
            "log",
            "--date=iso-strict",
            "--pretty=format:%H" + FIELD_SEPARATOR + "%h" + FIELD_SEPARATOR + "%s" + FIELD_SEPARATOR + "%an" + FIELD_SEPARATOR + "%ad",
            "-n",
            String.valueOf(pageSize),
            "--skip",
            String.valueOf(offset));
        if (StringUtils.hasText(ref)) {
            command.add(ref);
        }
        String output = execute(repositoryPath, command, "git 提交历史查询");
        List<GitCommitView> commits = parseCommitViews(output);
        return new CommitPage(commits, totalCount, offset + commits.size() < totalCount);
    }

    private CommitPage searchCommitPage(Path repositoryPath,
                                        String ref,
                                        String searchQuery,
                                        int pageNumber,
                                        int pageSize) throws IOException {
        List<String> command = createCommand(
            "git",
            "log",
            "--date=iso-strict",
            "--pretty=format:%H" + FIELD_SEPARATOR + "%h" + FIELD_SEPARATOR + "%s" + FIELD_SEPARATOR + "%an" + FIELD_SEPARATOR + "%ad");
        if (StringUtils.hasText(ref)) {
            command.add(ref);
        }
        String output = execute(repositoryPath, command, "git 提交历史搜索");
        List<GitCommitView> matchedCommits = new ArrayList<>();
        for (GitCommitView commit : parseCommitViews(output)) {
            if (matchesCommitSearch(commit, searchQuery)) {
                matchedCommits.add(commit);
            }
        }
        int totalCount = matchedCommits.size();
        int offset = pageNumber * pageSize;
        if (offset >= totalCount) {
            return new CommitPage(Collections.<GitCommitView>emptyList(), totalCount, false);
        }
        int endIndex = Math.min(offset + pageSize, totalCount);
        return new CommitPage(new ArrayList<>(matchedCommits.subList(offset, endIndex)), totalCount, endIndex < totalCount);
    }

    private int readCommitCount(Path repositoryPath, String ref) throws IOException {
        List<String> command = createCommand("git", "rev-list", "--count");
        if (StringUtils.hasText(ref)) {
            command.add(ref);
        } else {
            command.add("HEAD");
        }
        String output = execute(repositoryPath, command, "git 提交总数查询");
        String countText = firstNonBlankLine(output);
        if (!StringUtils.hasText(countText)) {
            return 0;
        }
        return Integer.parseInt(countText.trim());
    }

    private List<GitCommitView> parseCommitViews(String output) {
        if (!StringUtils.hasText(output)) {
            return Collections.emptyList();
        }

        List<GitCommitView> commits = new ArrayList<>();
        for (String line : splitLines(output)) {
            if (StringUtils.hasText(line)) {
                String[] parts = line.split(FIELD_SEPARATOR, -1);
                if (parts.length >= 5) {
                    GitCommitView view = new GitCommitView();
                    view.setHash(parts[0]);
                    view.setShortHash(parts[1]);
                    view.setSubject(parts[2]);
                    view.setAuthorName(parts[3]);
                    view.setAuthoredAt(parts[4]);
                    commits.add(view);
                }
            }
        }
        return commits;
    }

    private boolean matchesCommitSearch(GitCommitView commit, String searchQuery) {
        if (!StringUtils.hasText(searchQuery)) {
            return true;
        }
        String normalizedQuery = searchQuery.toLowerCase(Locale.ROOT);
        return containsIgnoreCase(commit.getHash(), normalizedQuery)
            || containsIgnoreCase(commit.getShortHash(), normalizedQuery)
            || containsIgnoreCase(commit.getSubject(), normalizedQuery)
            || containsIgnoreCase(commit.getAuthorName(), normalizedQuery);
    }

    private boolean containsIgnoreCase(String value, String normalizedQuery) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private String normalizeSearchQuery(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int resolvePageNumber(Integer pageNumber) {
        return pageNumber != null && pageNumber >= 0 ? pageNumber : 0;
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return 30;
        }
        return Math.min(pageSize, 100);
    }

    private void applyDiffOptions(List<String> command, GitDiffQueryRequest request) {
        if (Boolean.TRUE.equals(request.getCached())) {
            command.add("--cached");
        }
        if (StringUtils.hasText(request.getDiffRange())) {
            command.add(request.getDiffRange().trim());
        }
        List<String> pathspecs = request.getPathspecs();
        if (pathspecs != null && !pathspecs.isEmpty()) {
            command.add("--");
            for (String pathspec : pathspecs) {
                if (StringUtils.hasText(pathspec)) {
                    command.add(pathspec.trim());
                }
            }
        }
    }

    private List<String> readLines(Path repositoryPath, List<String> command) throws IOException {
        String text = execute(repositoryPath, command, "git diff 文件列表查询");
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (String line : splitLines(text)) {
            if (StringUtils.hasText(line)) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String readText(Path repositoryPath, List<String> command) throws IOException {
        return execute(repositoryPath, command, "git diff 内容查询");
    }

    private String execute(Path repositoryPath, List<String> command, String action) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repositoryPath.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readStream(process.getInputStream());
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(action + "失败: " + output.trim());
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(action + "被中断", ex);
        }
    }

    private List<String> createCommand(String... values) {
        List<String> command = new ArrayList<>();
        Collections.addAll(command, values);
        return command;
    }

    private String firstNonBlankLine(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (String line : splitLines(value)) {
            if (StringUtils.hasText(line)) {
                return line.trim();
            }
        }
        return null;
    }

    private String[] splitLines(String value) {
        return value.split(LINE_SPLIT_REGEX);
    }

    private String readStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

    private void applyDiffOutput(GitDiffView view, String diffOutput, Integer maxCharacters) {
        int effectiveMax = 20000;
        if (maxCharacters != null && maxCharacters > 0) {
            effectiveMax = maxCharacters;
        }
        if (diffOutput == null) {
            view.setDiffOutput("");
            view.setTruncated(Boolean.FALSE);
            return;
        }
        if (diffOutput.length() <= effectiveMax) {
            view.setDiffOutput(diffOutput);
            view.setTruncated(Boolean.FALSE);
            return;
        }
        view.setDiffOutput(diffOutput.substring(0, effectiveMax));
        view.setTruncated(Boolean.TRUE);
    }

    private static class ControllerDescriptor {
        private String controllerClass;
        private String relativePath;
        private String sourceText;
        private final List<ControllerMapping> mappings = new ArrayList<>();
    }

    private static class ClassDeclaration {
        private int classIndex;
        private String className;
        private String classPath;
    }

    private static class ControllerMapping {
        private String httpMethod;
        private String path;
        private String handlerMethod;
    }

    private static class CommitPage {
        private final List<GitCommitView> commits;
        private final int totalCount;
        private final boolean hasNextPage;

        private CommitPage(List<GitCommitView> commits, int totalCount, boolean hasNextPage) {
            this.commits = commits;
            this.totalCount = totalCount;
            this.hasNextPage = hasNextPage;
        }
    }
}