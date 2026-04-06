package com.apitestagent.service;

import com.apitestagent.web.dto.MethodSourceQueryRequest;
import com.apitestagent.web.dto.MethodSourceView;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class MethodSourceService {

    private static final String JAVA_EXTENSION = ".java";
    private static final String PACKAGE_PREFIX = "package ";

    public MethodSourceView findMethodSource(MethodSourceQueryRequest request) throws IOException {
        List<Path> candidateFiles = collectCandidateFiles(request);
        if (candidateFiles.isEmpty()) {
            throw new NoSuchElementException("未找到匹配的源码文件");
        }

        for (Path candidateFile : candidateFiles) {
            MethodSourceView result = extractMethodSource(candidateFile, request);
            if (result != null) {
                return result;
            }
        }

        throw new NoSuchElementException("找到类文件，但未定位到匹配的方法实现");
    }

    private List<Path> collectCandidateFiles(MethodSourceQueryRequest request) throws IOException {
        List<Path> searchRoots = resolveSearchRoots(request.getSearchRoots());
        List<Path> candidates = new ArrayList<>();
        final String classFileName = request.getClassName() + JAVA_EXTENSION;
        final String fileHint = normalize(request.getFileHint());

        for (Path searchRoot : searchRoots) {
            if (!Files.exists(searchRoot)) {
                continue;
            }
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String pathText = normalize(file.toString());
                    if (file.getFileName().toString().equals(classFileName)
                        && (!StringUtils.hasText(fileHint) || pathText.contains(fileHint))) {
                        candidates.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return candidates;
    }

    private List<Path> resolveSearchRoots(List<String> requestSearchRoots) {
        List<String> configuredRoots = requestSearchRoots;
        if (configuredRoots == null || configuredRoots.isEmpty()) {
            configuredRoots = new ArrayList<>();
            configuredRoots.add("src/main/java");
            configuredRoots.add("src/test/java");
        }

        List<Path> roots = new ArrayList<>();
        for (String configuredRoot : configuredRoots) {
            if (StringUtils.hasText(configuredRoot)) {
                roots.add(Paths.get(configuredRoot));
            }
        }
        return roots;
    }

    private MethodSourceView extractMethodSource(Path file, MethodSourceQueryRequest request) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (!matchesPackage(lines, request.getPackageName())) {
            return null;
        }
        String targetMethodName = request.getMethodName();
        for (int index = 0; index < lines.size(); index++) {
            String signature = collectMethodSignature(lines, index);
            if (isMethodDeclaration(signature, targetMethodName, request.getParameterCount())) {
                int endLine = findMethodEnd(lines, index);
                if (endLine < index) {
                    endLine = index;
                }
                return buildView(file, lines, index, endLine, signature);
            }
        }
        return null;
    }

    private boolean isMethodDeclaration(String signature, String methodName, Integer parameterCount) {
        if (!StringUtils.hasText(methodName)) {
            return false;
        }
        String trimmed = signature.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
            return false;
        }
        String signatureToken = methodName + "(";
        if (!trimmed.contains(signatureToken)
            || trimmed.startsWith("if ")
            || trimmed.startsWith("for ")
            || trimmed.startsWith("while ")
            || trimmed.startsWith("switch ")) {
            return false;
        }
        if (parameterCount == null) {
            return true;
        }
        return countParameters(trimmed) == parameterCount;
    }

    private boolean matchesPackage(List<String> lines, String packageName) {
        if (!StringUtils.hasText(packageName)) {
            return true;
        }
        String normalizedPackage = packageName.trim();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(PACKAGE_PREFIX)) {
                String actualPackage = trimmed.substring(PACKAGE_PREFIX.length()).replace(";", "").trim();
                return normalizedPackage.equals(actualPackage);
            }
        }
        return false;
    }

    private String collectMethodSignature(List<String> lines, int startLine) {
        StringBuilder builder = new StringBuilder();
        boolean stopCollecting = false;
        for (int index = startLine; index < lines.size(); index++) {
            String current = lines.get(index).trim();
            if (!current.isEmpty()) {
                builder.append(current).append(' ');
                stopCollecting = current.contains("{") || current.endsWith(";") || current.contains(")");
            }
            if (stopCollecting) {
                break;
            }
        }
        return builder.toString().trim();
    }

    private int countParameters(String signature) {
        int start = signature.indexOf('(');
        int end = signature.indexOf(')', start + 1);
        if (start < 0 || end < 0 || end <= start + 1) {
            return 0;
        }
        String parameters = signature.substring(start + 1, end).trim();
        if (!StringUtils.hasText(parameters)) {
            return 0;
        }
        int count = 1;
        int genericDepth = 0;
        for (int index = 0; index < parameters.length(); index++) {
            char current = parameters.charAt(index);
            if (current == '<') {
                genericDepth++;
            } else if (current == '>') {
                genericDepth--;
            } else if (current == ',' && genericDepth == 0) {
                count++;
            }
        }
        return count;
    }

    private int findMethodEnd(List<String> lines, int startLine) {
        int braceBalance = 0;
        boolean started = false;
        for (int index = startLine; index < lines.size(); index++) {
            String currentLine = lines.get(index);
            for (int i = 0; i < currentLine.length(); i++) {
                char current = currentLine.charAt(i);
                if (current == '{') {
                    braceBalance++;
                    started = true;
                } else if (current == '}') {
                    braceBalance--;
                }
            }
            if (started && braceBalance <= 0) {
                return index;
            }
        }
        return startLine;
    }

    private MethodSourceView buildView(Path file, List<String> lines, int startLine, int endLine, String matchedSignature) {
        List<String> snippetLines = new ArrayList<>();
        for (int index = startLine; index <= endLine && index < lines.size(); index++) {
            snippetLines.add(lines.get(index));
        }

        MethodSourceView view = new MethodSourceView();
        view.setFilePath(file.toString());
        view.setStartLine(startLine + 1);
        view.setEndLine(endLine + 1);
        view.setMatchedSignature(matchedSignature.trim());
        view.setSourceCode(joinLines(snippetLines));
        return view;
    }

    private String joinLines(List<String> lines) {
        if (lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            builder.append(lines.get(index));
            if (index < lines.size() - 1) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
