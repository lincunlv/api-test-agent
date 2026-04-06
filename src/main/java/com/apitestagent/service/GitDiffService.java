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

import com.apitestagent.web.dto.GitDiffQueryRequest;
import com.apitestagent.web.dto.GitDiffView;
import com.apitestagent.web.dto.RelatedInterfaceView;

@Service
public class GitDiffService {

    private static final String JAVA_EXTENSION = ".java";
    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+(\\w+)");
    private static final Pattern REQUEST_MAPPING_PATTERN = Pattern.compile("@RequestMapping\\((.*)\\)");
    private static final Pattern QUOTED_PATH_PATTERN = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.(GET|POST|PUT|DELETE|PATCH)");

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
        String[] lines = diffOutput.split("\\r?\\n");
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
            mapping.httpMethod = "DELETE";
            mapping.path = extractQuotedPath(line);
            return mapping;
        }
        if (line.startsWith("@PatchMapping")) {
            mapping.httpMethod = "PATCH";
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
            return "DIRECT";
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
        String text = execute(repositoryPath, command);
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            if (StringUtils.hasText(line)) {
                lines.add(line.trim());
            }
        }
        return lines;
    }

    private String readText(Path repositoryPath, List<String> command) throws IOException {
        return execute(repositoryPath, command);
    }

    private String execute(Path repositoryPath, List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(repositoryPath.toFile());
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        String output = readStream(process.getInputStream());
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("git diff 执行失败: " + output.trim());
            }
            return output;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git diff 执行被中断", ex);
        }
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
}