package com.apitestagent.engine;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.apitestagent.domain.SkillBundle;
import com.apitestagent.domain.SkillType;
import com.apitestagent.web.dto.CreateTaskRequest;

@Component
public class TemplateAnalysisRenderer implements AnalysisRenderer {

    private static final String INTERFACE_KEY = "interface";
    private static final String TABLE_SEPARATOR = "| --- | --- | --- | --- | --- | --- | --- |\n";
    private static final String DIFF_TABLE_SEPARATOR = "| --- | --- | --- | --- | --- | --- | --- | --- |\n";

    @Override
    public String render(CreateTaskRequest request, SkillType skillType, SkillBundle skillBundle, String taskId) {
        String interfaceName = valueFrom(request.getChainData(), INTERFACE_KEY, "name", "未提供接口名称");
        String interfacePath = valueFrom(request.getChainData(), INTERFACE_KEY, "path", "未提供接口路径");
        String interfaceMethod = valueFrom(request.getChainData(), INTERFACE_KEY, "method", "未提供请求方法");

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(skillType.getDisplayName()).append("\n\n");
        builder.append("> 当前文件由工程骨架自动生成，用于验证 Skill 路由、工作空间落盘和输出结构。真实分析内容将在接入模型执行引擎后填充。\n\n");
        builder.append("## 任务元信息\n\n");
        builder.append("- taskId: ").append(taskId).append("\n");
        builder.append("- skill: ").append(skillType.getCode()).append("\n");
        builder.append("- 用户请求: ").append(request.getPrompt()).append("\n");
        builder.append("- 接口名称: ").append(interfaceName).append("\n");
        builder.append("- 接口路径: ").append(interfacePath).append("\n");
        builder.append("- 请求方法: ").append(interfaceMethod).append("\n\n");

        switch (skillType) {
            case A1:
                builder.append("## 业务目标\n\n待补充\n\n");
                builder.append("## 主流程\n\n待补充\n\n");
                builder.append("## 关键分支\n\n待补充\n\n");
                builder.append("## 外部依赖\n\n待补充\n\n");
                builder.append("## Mermaid 流程图\n\n```mermaid\nflowchart TD\n    A[开始] --> B[待接入模型分析]\n```\n\n");
                builder.append("## 待确认项\n\n- 链路源码细节待补充\n\n");
                break;
            case A2:
                builder.append("## 风险清单\n\n");
                builder.append("| 风险编号 | 风险描述 | 触发条件 | 影响范围 | 风险等级 | 建议验证方式 | 证据来源 |\n");
                builder.append(TABLE_SEPARATOR);
                builder.append("| R-001 | 待模型分析补充 | 待补充 | 待补充 | 待评估 | 待补充 | 链路数据 |\n\n");
                builder.append("## 待确认项\n\n- 异常处理和边界分支待补充\n\n");
                break;
            case A3:
                builder.append("## 测试用例\n\n");
                builder.append("| 用例编号 | 用例标题 | 优先级 | 前置条件 | 测试步骤 | 预期结果 | 覆盖点 |\n");
                builder.append(TABLE_SEPARATOR);
                builder.append("| TC-001 | 待模型分析补充 | P1 | 待补充 | 待补充 | 待补充 | 主流程 |\n\n");
                builder.append("## 待确认项\n\n- 环境依赖和测试数据待补充\n\n");
                break;
            case A4:
                builder.append("## 变更摘要\n\n待补充\n\n");
                builder.append("## 关联接口识别\n\n待补充\n\n");
                builder.append("## 增量测试用例\n\n");
                builder.append("| 用例编号 | 关联接口 | 变更点 | 触发原因 | 用例标题 | 优先级 | 测试步骤 | 预期结果 |\n");
                builder.append(DIFF_TABLE_SEPARATOR);
                builder.append("| DTC-001 | 待补充 | 待补充 | 待补充 | 待模型分析补充 | P1 | 待补充 | 待补充 |\n\n");
                builder.append("## 回归建议\n\n- 必回归：待补充\n- 建议回归：待补充\n- 暂不回归：待补充\n\n");
                builder.append("## 待确认项\n\n- git diff 影响范围和接口映射待补充\n\n");
                break;
            case A5:
                builder.append("## 接口概述\n\n待补充\n\n");
                builder.append("## 请求定义\n\n待补充\n\n");
                builder.append("## 处理逻辑摘要\n\n待补充\n\n");
                builder.append("## 响应定义\n\n待补充\n\n");
                builder.append("## 异常场景\n\n待补充\n\n");
                builder.append("## 注意事项\n\n待补充\n\n");
                break;
            default:
                throw new IllegalStateException("不支持的 skillType: " + skillType);
        }

        builder.append("## Skill 执行依据\n\n");
        builder.append("### SKILL.md\n\n");
        builder.append(skillBundle.getSkillIndexContent()).append("\n\n");
        builder.append("### Reference\n\n");
        builder.append(skillBundle.getReferenceContent()).append("\n");
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private String valueFrom(Map<String, Object> root, String firstKey, String secondKey, String fallback) {
        if (root == null) {
            return fallback;
        }
        Object first = root.get(firstKey);
        if (!(first instanceof Map)) {
            return fallback;
        }
        Object second = ((Map<String, Object>) first).get(secondKey);
        return second == null ? fallback : String.valueOf(second);
    }
}
