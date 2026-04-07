# 接口测试智能体 API 接口文档

## 1. 文档说明

本文档用于说明当前服务已开放的 HTTP 接口、请求体结构、响应体要点和错误码约定，适合前端、平台和后端联调使用。

基础地址：

`http://localhost:8080`

## 2. 通用说明

### 2.1 请求头

推荐传递：

1. `Content-Type: application/json`
2. `X-Request-Id: 自定义请求ID`，可选

如果未传 `X-Request-Id`，服务会自动生成。

### 2.2 通用错误码

当前统一错误码如下：

1. `VALIDATION_ERROR`：参数缺失或请求体不合法
2. `BAD_REQUEST`：业务参数非法
3. `NOT_FOUND`：资源不存在
4. `INTERNAL_ERROR`：服务内部异常

## 3. 创建任务

### 3.1 接口

`POST /api/agent/tasks`

### 3.2 请求体

```json
{
  "taskType": "A3",
  "prompt": "请生成测试用例",
  "chainData": {
    "interface": {
      "name": "createOrder",
      "path": "/api/order/create",
      "method": "POST"
    },
    "entry": {
      "class_name": "OrderService",
      "method_name": "createOrder",
      "package_name": "com.demo.order.service",
      "parameter_count": 2
    }
  },
  "options": {
    "rendererMode": "template"
  }
}
```

### 3.3 字段说明

#### 顶层字段

1. `taskType`：必填。支持 `A1`、`A2`、`A3`、`A4`、`A5`。
2. `prompt`：必填。用户任务描述。
3. `chainData`：必填。接口链路数据。
4. `options`：可选。执行选项。

#### `chainData.interface`

1. `name`：接口名称
2. `path`：接口路径
3. `method`：HTTP 方法

#### `chainData.entry`

1. `class_name`：入口类名
2. `method_name`：入口方法名
3. `package_name`：包名，可选但推荐
4. `parameter_count`：参数个数，可选但推荐

#### `options`

1. `rendererMode`：`template` / `mock-model` / `mock-failure`
2. `mockOutput`：仅 `mock-model` 时生效
3. `mockFailureMessage`：仅 `mock-failure` 时生效
4. `methodSourceQuery`：可手动指定源码查询条件
5. `gitDiffQuery`：可选。自动采集 git diff 代码变更并写入任务上下文

不同 `taskType` 的典型用途如下：

1. `A1`：业务逻辑梳理，输出主流程、关键分支和测试关注点。
2. `A2`：Bug 风险分析，输出风险清单、观测点和优先验证建议。
3. `A3`：测试用例生成，输出测试用例、测试数据和核心观测点。
4. `A4`：Diff 场景级测试脚本生成，输出基于改动的受影响场景、增量测试脚本、关联接口和回归脚本建议。
5. `A5`：API 文档生成，输出接口说明和测试联调提示。

### 3.4 成功响应示例

```json
{
  "taskId": "a2b3c4d5",
  "skillCode": "A3",
  "skillName": "测试用例生成",
  "status": "PARTIAL_SUCCESS",
  "prompt": "请生成测试用例",
  "workspacePath": "workspace/agent_files/a3/a2b3c4d5",
  "artifacts": [
    "input.json",
    "source-context.md",
    "test-cases.md",
    "trace-summary.json"
  ],
  "message": "已生成结构化草稿，并自动补充了源码上下文；待接入真实模型执行引擎。",
  "lastError": null,
  "executionEventCount": 6,
  "sourceLookupApplied": true,
  "createdAt": "2026-04-05T10:00:00Z",
  "updatedAt": "2026-04-05T10:00:02Z"
}
```

### 3.5 失败响应示例

参数缺失：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "taskType 不能为空"
}
```

任务类型非法：

```json
{
  "code": "BAD_REQUEST",
  "message": "无法识别的 taskType: UNKNOWN"
}
```

内部异常：

```json
{
  "code": "INTERNAL_ERROR",
  "message": "forced renderer failure"
}
```

## 4. 查询任务

### 4.1 接口

`GET /api/agent/tasks/{taskId}`

### 4.2 功能

1. 查询任务当前状态
2. 查看结果产物
3. 查看事件计数
4. 查看错误信息

### 4.3 成功响应

返回结构与创建任务成功响应一致。

### 4.4 失败响应

任务不存在：

```json
{
  "code": "NOT_FOUND",
  "message": "任务不存在: missing-task-id"
}
```

## 5. 查询任务产物内容

### 5.1 接口

`GET /api/agent/tasks/{taskId}/artifacts/{artifactName}`

### 5.2 功能

1. 按任务 ID 读取指定产物内容。
2. 支持预览 `json`、`jsonl`、`md`、`patch`、`txt` 等文本类文件。
3. 用于前端任务结果区的在线预览。

### 5.3 成功响应示例

```json
{
  "taskId": "a2b3c4d5",
  "artifactName": "trace-summary.json",
  "contentType": "application/json",
  "size": 512,
  "content": "{\n  \"taskId\": \"a2b3c4d5\"\n}"
}
```

### 5.4 失败响应示例

任务产物不存在：

```json
{
  "code": "NOT_FOUND",
  "message": "任务产物不存在: missing.md"
}
```

## 6. 方法源码查询

### 5.1 接口

`POST /api/agent/tools/get-method-source`

### 5.2 请求体

```json
{
  "className": "AgentTaskService",
  "methodName": "createTask",
  "packageName": "com.apitestagent.service",
  "parameterCount": 1,
  "fileHint": "service",
  "searchRoots": [
    "src/main/java",
    "src/test/java"
  ]
}
```

### 5.3 字段说明

1. `className`：必填。类名。
2. `methodName`：必填。方法名。
3. `packageName`：可选。包名，用于缩小匹配范围。
4. `parameterCount`：可选。参数个数。
5. `fileHint`：可选。文件路径提示词。
6. `searchRoots`：可选。搜索根路径列表。

### 5.4 成功响应示例

```json
{
  "filePath": "src/main/java/com/apitestagent/service/AgentTaskService.java",
  "startLine": 60,
  "endLine": 130,
  "matchedSignature": "public TaskView createTask(CreateTaskRequest request) throws IOException {",
  "sourceCode": "public TaskView createTask(CreateTaskRequest request) throws IOException { ... }"
}
```

### 5.5 失败响应示例

参数缺失：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "className 不能为空"
}
```

未找到方法：

```json
{
  "code": "NOT_FOUND",
  "message": "未找到匹配的源码文件"
}
```

## 7. Git Diff 查询

### 6.1 接口

`POST /api/agent/tools/get-git-diff`

### 6.2 请求体

```json
{
  "repositoryPath": "g:/api-test-agent",
  "diffRange": "HEAD~1..HEAD",
  "cached": false,
  "maxCharacters": 20000,
  "pathspecs": [
    "src/main/java",
    "src/test/java"
  ]
}
```

### 6.3 字段说明

1. `repositoryPath`：可选。git 仓库路径，默认使用当前工作目录。
2. `diffRange`：可选。比如 `HEAD~1..HEAD`。
3. `cached`：可选。是否读取 staged diff。
4. `maxCharacters`：可选。最大返回字符数，默认 `20000`。
5. `pathspecs`：可选。限制 diff 的路径范围。

### 6.4 成功响应示例

```json
{
  "repositoryPath": "g:\\api-test-agent",
  "diffRange": null,
  "cached": false,
  "truncated": false,
  "changedFiles": [
    "src/main/java/com/apitestagent/service/AgentTaskService.java"
  ],
  "changedClasses": [
    "AgentTaskService"
  ],
  "changedMethods": [
    "createTask"
  ],
  "relatedInterfaces": [
    {
      "controllerClass": "AgentTaskController",
      "handlerMethod": "createTask",
      "httpMethod": "POST",
      "path": "/api/agent/tasks",
      "relationType": "INDIRECT",
      "evidence": "引用变更类: AgentTaskService",
      "sourceFile": "src/main/java/com/apitestagent/web/AgentTaskController.java"
    }
  ],
  "diffOutput": "diff --git a/src/main/java/..."
}
```

响应中的关键字段说明：

1. `changedFiles`：本次 diff 影响的文件列表。
2. `changedClasses`：从变更文件推导出的类级影响对象。
3. `changedMethods`：从 diff 文本中抽取出的可能受影响方法。
4. `relatedInterfaces`：结合代码引用关系识别出的直接或间接受影响接口，可直接作为 A4 生成场景级测试脚本时的结构化证据输入。
5. `scenarioCandidates`：基于受影响 Controller、接口链和变更方法推导出的场景候选，包含业务对象、共享主键、数据传递提示、响应字段、请求绑定、字段传递提示、状态流转提示、依赖提示等信息，可直接作为 A4 的受影响场景输入。
6. `diffOutput`：原始 diff 文本，用于人工复核。

### 6.5 失败响应示例

仓库不存在：

```json
{
  "code": "BAD_REQUEST",
  "message": "repositoryPath 不存在: g:/missing-repo"
}
```

非 git 仓库：

```json
{
  "code": "BAD_REQUEST",
  "message": "指定目录不是 git 仓库: g:/plain-folder"
}
```

## 8. Git 历史查询

### 8.1 接口

`POST /api/agent/tools/get-git-history`

### 8.2 请求体

```json
{
  "repositoryPath": "g:/api-test-agent-service/api-test-agent",
  "ref": "HEAD",
  "searchQuery": "createTask",
  "pageNumber": 0,
  "maxCount": 30
}
```

### 8.3 字段说明

1. `repositoryPath`：可选。git 仓库路径，默认使用当前工作目录。
2. `ref`：可选。指定查询哪条引用对应的提交历史，默认当前分支。
3. `searchQuery`：可选。按 commit hash、短 hash、提交标题、作者名模糊搜索。
4. `pageNumber`：可选。分页页码，从 `0` 开始。
5. `maxCount`：可选。每页返回提交数量，默认 `30`，最大 `100`。

### 8.4 成功响应示例

```json
{
  "repositoryPath": "g:\\api-test-agent-service\\api-test-agent",
  "currentBranch": "master",
  "resolvedRef": "master",
  "searchQuery": "createTask",
  "pageNumber": 0,
  "pageSize": 30,
  "totalCount": 12,
  "hasNextPage": false,
  "refs": [
    {
      "name": "master",
      "fullName": "refs/heads/master",
      "type": "BRANCH",
      "target": "56c3754",
      "updatedAt": "2026-04-06T16:00:00+08:00"
    }
  ],
  "commits": [
    {
      "hash": "56c3754a1b2c3d4e5f6",
      "shortHash": "56c3754",
      "subject": "Initial commit",
      "authorName": "GitHub Copilot",
      "authoredAt": "2026-04-06T16:00:00+08:00"
    }
  ]
}
```

## 9. rendererMode 约定

### 6.1 `template`

默认模式，返回模板化结构内容。

### 6.2 `mock-model`

返回 `mockOutput` 指定的内容。

示例：

```json
{
  "options": {
    "rendererMode": "mock-model",
    "mockOutput": "# mock-output\n\nmock renderer content"
  }
}
```

### 6.3 `mock-failure`

主动抛出错误，用于验证 500 响应和失败归档。

示例：

```json
{
  "options": {
    "rendererMode": "mock-failure",
    "mockFailureMessage": "forced renderer failure"
  }
}
```

## 10. 任务状态说明

当前任务状态包括：

1. `PENDING`
2. `RUNNING`
3. `PARTIAL_SUCCESS`
4. `FAILED`

当前骨架版本下，大多数成功任务会落在 `PARTIAL_SUCCESS`，含义是：

1. 工作流执行成功
2. 结果文件已经生成
3. 但分析内容仍是模板草稿，尚未接入真实模型

## 11. 归档文件说明

### 8.1 `task.json`

任务状态快照。

### 8.2 `execution-events.jsonl`

按阶段写入事件流水。常见阶段：

1. `SUBMITTED`
2. `STATUS_CHANGED`
3. `INPUT_PERSISTED`
4. `SKILL_LOADED`
5. `GIT_DIFF_SKIPPED` / `GIT_DIFF_COLLECTED`
6. `SOURCE_LOOKUP_SKIPPED` / `SOURCE_LOOKUP_FOUND`
7. `ARTIFACT_WRITTEN`
8. `COMPLETED` / `FAILED`

### 8.3 `trace-summary.json`

任务摘要，包括：

1. `status`
2. `artifacts`
3. `executionEventCount`
4. `sourceLookupApplied`
5. `lastError`

### 11.4 `git-diff.patch`

当请求启用了 `options.gitDiffQuery` 时，自动归档当前采集到的 git diff 内容。

### 11.5 `diff-impact.json`

当任务类型为 `A4` 且启用了 `options.gitDiffQuery` 时，自动归档结构化变更影响摘要，通常包含：

1. `changedFiles`
2. `changedClasses`
3. `changedMethods`
4. `relatedInterfaces`
5. `scenarioCandidates`

该文件适合前端展示、场景测试脚本生成、回归范围评估和后续自动化处理。

## 12. 联调建议

推荐联调顺序：

1. 先调 `mock-model`，验证任务接口和产物读取。
2. 再调 `get-method-source`，验证源码补查能力。
3. 再调 `get-git-diff`，验证代码变更采集是否准确。
4. 再调 `mock-failure`，验证 500 响应和失败归档。
5. 最后再接真实模型调用。
