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
    private static final String DIFF_SCENARIO_TABLE_SEPARATOR = "| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n";
    private static final String DIFF_SCRIPT_TABLE_SEPARATOR = "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n";

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
                builder.append("## 测试脚本生成判断\n\n待补充\n\n");
                builder.append("## 关联接口识别\n\n待补充\n\n");
                builder.append("## 受影响场景识别\n\n");
                builder.append("| 场景编号 | 场景名称 | 场景类型 | 入口接口 | 关联接口链 | 共享业务对象/状态提示 | 触发条件 | 场景依据 | 建议优先级 |\n");
                builder.append(DIFF_SCENARIO_TABLE_SEPARATOR);
                builder.append("| SCN-001 | 待补充 | 主流程/状态流转 | 待补充 | 待补充 | 业务对象/状态待补充 | 待补充 | 待模型分析补充 | P1 |\n\n");
                builder.append("## 增量测试脚本\n\n");
                builder.append("| 脚本编号 | 脚本分类 | 关联场景 | 关联接口 | 变更点 | 触发原因 | 优先级 | 请求数据/输入 | 执行步骤/脚本骨架 | 断言 | 核心观测点 |\n");
                builder.append(DIFF_SCRIPT_TABLE_SEPARATOR);
                builder.append("| DTS-001 | 直接受影响场景 | SCN-001 | 待补充 | 待补充 | 待补充 | P1 | 待补充 | 待模型分析补充 | 待补充 | 返回字段/状态码/状态流转 |\n\n");
                builder.append("## 回归补充脚本\n\n");
                builder.append("| 脚本编号 | 回归级别 | 回归对象 | 关联场景 | 建议补充脚本 | 原因 |\n");
                builder.append("| --- | --- | --- | --- | --- | --- |\n");
                builder.append("| RTS-001 | 必回归 | 待补充 | SCN-001 | 待补充 | 待补充 |\n\n");
                builder.append("## 执行顺序建议\n\n- 1. 先执行直接受影响场景脚本\n- 2. 再执行高风险关联场景回归脚本\n- 3. 最后执行低风险补充验证脚本\n\n");
                builder.append("## 待确认项\n\n- git diff 影响范围、接口映射和完整场景链待补充\n\n");
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
