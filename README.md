# API Test Agent

基于 Java 8 和 Spring Boot 2.7.6 的接口测试智能体工程骨架。

详细使用方式见 [docs/使用说明文档.md](docs/%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E%E6%96%87%E6%A1%A3.md)。
研发联调接口说明见 [docs/API接口文档.md](docs/API%E6%8E%A5%E5%8F%A3%E6%96%87%E6%A1%A3.md)。

## 当前能力

1. 提供任务创建与任务查询接口。
2. 根据任务类型自动路由到 A1、A2、A3、A4、A5 五类 Skill。
3. 生成会话级工作目录、任务元数据、执行事件归档和 trace 摘要文件。
4. 提供真实的任务状态流转：`PENDING -> RUNNING -> PARTIAL_SUCCESS/FAILED`。
5. 提供请求级 requestId 透传与基础访问日志。
6. 提供增强版 `get-method-source` API，可按类名、方法名、包名、参数个数定位本地 Java 源码片段。
7. 提供 `get-git-diff` API，可按仓库路径、diffRange 和路径范围获取代码变更。
8. 任务执行时可根据链路 `entry` 或 `options.methodSourceQuery` 自动补充源码上下文。
9. A4 Diff 关联接口用例生成任务支持通过 `options.gitDiffQuery` 自动采集 git diff，并落盘为 `git-diff.patch`。
10. 分析引擎已接入统一中间件管道，当前包含 Guardrail、ContextEditing、Observability、Retry、ErrorBoundary 五类处理。
11. 已预留可配置 renderer 接缝，可通过 `agent.analysis.renderer-mode` 或请求 `options.rendererMode` 切换到后续模型适配实现。
12. 已预留 `llm` 渲染模式和 `LlmAnalysisAdapter` 适配器接口，可在不改任务主流程的前提下接入真实模型。

当前默认提供的是 OpenAI 兼容 HTTP 适配器骨架，需要补充 `llm-base-url`、`llm-api-key`、`llm-model` 等配置后才能接真实模型服务。

当前版本是可运行骨架，已将 Skill 模板、工作空间输出、任务持久化和自动源码补查打通；真实模型执行引擎、上下文治理中间件和更强的 AST 级语义解析仍待补充。

## 技术基线

1. Java 8
2. Spring Boot 2.7.6
3. Maven

## 启动方式

## 本地 MySQL 持久化配置

服务已默认接入本地 MySQL，并使用以下默认值：

1. 主机：`127.0.0.1`
2. 端口：`3306`
3. 数据库：`api_test_agent`
4. 用户名：`root`
5. 密码：`root123456`

首次启动时会通过 JDBC 参数自动创建 `api_test_agent` 数据库，便于本地测试数据持久化。

服务启动时会通过 Flyway 自动执行数据库版本脚本，初始化 3 张核心表：`agent_task`、`agent_task_event`、`agent_task_artifact`。

当前这 3 张表先用于承接后续数据库持久化改造的核心模型：

1. `agent_task`：任务主记录。
2. `agent_task_event`：任务执行事件流水。
3. `agent_task_artifact`：任务产物元数据与可选正文。

当前数据库访问层按 MyBatis-Plus 组织，后续任务、事件、产物持久化统一通过 Mapper 层处理。

如需覆盖默认值，可通过环境变量传入：`DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`。

### Windows

```bat
run.bat
```

### Maven

```bash
mvn spring-boot:run
```

## 示例接口

### 创建任务

```bash
curl -X POST http://localhost:8080/api/agent/tasks ^
  -H "Content-Type: application/json" ^
  -d "{\"taskType\":\"A3\",\"prompt\":\"请生成测试用例\",\"chainData\":{\"interface\":{\"name\":\"createOrder\",\"path\":\"/api/order/create\",\"method\":\"POST\"}}}"
```

### 查询任务

```bash
curl http://localhost:8080/api/agent/tasks/{taskId}
```

任务执行后，工作目录下会生成这些关键文件：

1. `task.json`：任务当前状态快照。
2. `execution-events.jsonl`：按阶段追加的执行事件日志。
3. `trace-summary.json`：任务摘要和产物索引。
4. `source-context.md`：自动补充的源码上下文（命中时生成）。
5. `git-diff.patch`：自动采集的代码变更内容（启用 `options.gitDiffQuery` 时生成）。

### 查询方法源码

```bash
curl -X POST http://localhost:8080/api/agent/tools/get-method-source ^
  -H "Content-Type: application/json" ^
  -d "{\"className\":\"AgentTaskService\",\"methodName\":\"createTask\",\"packageName\":\"com.apitestagent.service\",\"parameterCount\":1}"
```

### 查询 git diff

```bash
curl -X POST http://localhost:8080/api/agent/tools/get-git-diff ^
  -H "Content-Type: application/json" ^
  -d "{\"repositoryPath\":\"g:/api-test-agent\",\"pathspecs\":[\"src/main/java\"]}"
```

## 目录说明

```text
src/main/java/com/apitestagent
  config
  domain
  engine
  service
  web
skills/interface-chain-analyzer
workspace/agent_files
docs
```

## 后续待实现

1. 接入真实 LLM/Agent Runtime。
2. 接入监控、上下文治理和重试中间件。
3. 将当前增强版源码检索继续升级为 AST/语义级实现。
4. 增加自动化测试和回归样例。