package com.apitestagent.domain;

import java.util.Locale;

public enum SkillType {
    A1("A1", "业务逻辑梳理", "A1-logic.md", "logic-analysis.md"),
    A2("A2", "Bug 风险分析", "A2-bug.md", "risk-analysis.md"),
    A3("A3", "测试用例生成", "A3-cases.md", "test-cases.md"),
    A4("A4", "Diff 关联接口用例生成", "A4-diff.md", "diff-test-cases.md"),
    A5("A5", "API 文档生成", "A5-doc.md", "api-doc.md");

    private final String code;
    private final String displayName;
    private final String referenceFile;
    private final String artifactFileName;

    SkillType(String code, String displayName, String referenceFile, String artifactFileName) {
        this.code = code;
        this.displayName = displayName;
        this.referenceFile = referenceFile;
        this.artifactFileName = artifactFileName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getReferenceFile() {
        return referenceFile;
    }

    public String getArtifactFileName() {
        return artifactFileName;
    }

    public static SkillType fromTaskType(String rawTaskType) {
        if (rawTaskType == null || rawTaskType.trim().isEmpty()) {
            throw new IllegalArgumentException("taskType 不能为空");
        }

        String normalized = rawTaskType.trim().toLowerCase(Locale.ROOT);
        if ("a1".equals(normalized) || normalized.contains("逻辑") || normalized.contains("业务")) {
            return A1;
        }
        if ("a2".equals(normalized) || normalized.contains("bug") || normalized.contains("风险")) {
            return A2;
        }
        if ("a3".equals(normalized) || normalized.contains("case") || normalized.contains("用例") || normalized.contains("测试")) {
            return A3;
        }
        if ("a4".equals(normalized) || normalized.contains("diff") || normalized.contains("变更")
            || normalized.contains("增量") || normalized.contains("回归")) {
            return A4;
        }
        if ("a5".equals(normalized) || normalized.contains("doc") || normalized.contains("文档") || normalized.contains("api")) {
            return A5;
        }
        throw new IllegalArgumentException("无法识别的 taskType: " + rawTaskType);
    }
}
