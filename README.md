# NovaAgent · Phase 1

> 一个极简的 Java Agent CLI —— ReAct（思考 · 行动 · 观察）单循环，灵感来自
> *PaiCLI* 的第一期。本 README **仅适用于 Phase 1 里程碑** —— 后面还有
> 20+ 期要做。

---

## 1. Phase 1 是什么？

NovaAgent 诞生于一个学习项目：从零开始重建一个成熟的 Java Agent CLI，
一期一期地吸收。Phase 1 故意把"表面积"压到最小，但仍然跑通后续所有期
都会扩展的**端到端**主流程。

**Phase 1 已具备的能力**

| 能力                  | 详情                                                                 |
| --------------------- | -------------------------------------------------------------------- |
| ReAct Agent 循环      | `Think → Act → Observe`，上限 `agent.maxSteps`（默认 8）             |
| LLM 客户端            | OpenAI Chat Completions 协议 —— 兼容 GLM / DeepSeek / Kimi / StepFun 等所有同类端点 |
| 流式输出              | 解析 SSE 的 `delta.content`；CLI 逐 token 实时打印                   |
| 工具（7 + 1）         | `read_file`、`write_file`、`list_dir`、`glob_files`、`grep_code`、`execute_command`、`create_project`、`web_search` |
| 项目根目录安全        | 所有文件工具拒绝离开项目根目录（PathGuard）                          |
| Markdown 友好的 CLI   | 启动横幅、颜色（非 TTY / `NO_COLOR` 时自动关闭）、不渲染表格的 Markdown 渲染器 |
| 交互式 REPL           | JLine 3，支持行编辑、历史、斜杠命令                                  |
| 配置分层              | `application.properties` → `~/.novaagent/config.properties` → 环境变量 |
| 为持久化做准备        | 单次会话的聊天历史先在内存里累积；后续期再导出                        |

**Phase 1 明确不做的事**

- 不做 Plan-and-Execute / DAG（Phase 2）
- 不做长期记忆或自动摘要（Phase 3）
- 不做 RAG embedding（Phase 4）
- 不做多 agent 角色（Phase 5）
- 不做 HITL / 审批流（Phase 6）
- 不做并行工具调用（Phase 7）—— 当前一轮只串行执行一个
- 不做运行时多模型切换（Phase 8 部分实现；`api.model` 切换需重启）
- 不做 MCP 集成（Phase 10）
- 不做 `web_fetch`（计划在 Phase 9）

---

## 2. Phase 1 如何实现 ReAct

```
┌────────────┐                              ┌────────────┐
│  CLI REPL  │  用户输入（原始文本）        │  工具层     │
│ (JLine 3)  │ ───────────────────────────▶ │ (Registry) │
└─────┬──────┘                              └─────┬──────┘
      │                                          │
      │ system + history + tools schema          │ invoke(name, args)
      ▼                                          │
┌────────────┐    assistant content              ▼
│  LLM       │ ◀── (或 tool_calls)────────  工具结果（字符串）
│  (OpenAI   │
│  compatible │    tool_results role=tool  ────▶ history 增长
│   HTTP)    │
└────────────┘
      │
      │ 当 finish_reason = "stop" → 打印最终 Markdown
      ▼
  回到 REPL
```

ReAct 循环实现在 `com.novaagent.agent.Agent` 中。每一步：

1. 把完整 history + 工具描述发给 LLM。
2. 如果 LLM 返回 tool call，则**串行**执行（并行留到 Phase 7）。
3. 把工具结果作为 `role=tool` 消息加回去，让下一轮 LLM 能看到。
4. 直到 LLM 返回 `finish_reason=stop` 或达到步数上限才停止。

系统提示词位于 `src/main/resources/prompts/phase-1/system.md`，可以被
`~/.novaagent/prompts/phase-1/system.md`（用户级）或
`<project>/.novaagent/prompts/phase-1/system.md`（项目级，优先级更高）覆盖。

---

## 3. Phase 1 的工具

所有工具都实现 `com.novaagent.tool.Tool`，只有两个方法：

```java
ToolSpec.FunctionSpec spec();            // name + description + JSON Schema
String invoke(Map<String,Object> args);  // 返回纯文本（成功或错误）
```

| 工具              | 用途                                              | 安全策略                                                       |
| ----------------- | ------------------------------------------------- | -------------------------------------------------------------- |
| `read_file`       | 读文本文件，上限 1 MB                             | PathGuard 限制在项目根目录内                                   |
| `write_file`      | 覆盖写文本文件，上限 5 MB                         | PathGuard 限制在项目根目录内                                   |
| `list_dir`        | 列目录（可选递归）                                | PathGuard；跳过 `target/`、`node_modules/`、`.git/` 等         |
| `glob_files`      | 按 glob 匹配（`src/**/*.java` 等）                | PathGuard；同上的跳过列表                                      |
| `grep_code`       | 在类代码文件里做正则/子串搜索                     | PathGuard；跳过构建目录                                        |
| `execute_command` | 在项目根目录里跑 shell 命令                       | 黑名单（`rm -rf /`、`mkfs`、fork 炸弹、`shutdown`…）；默认 60s 超时，输出截断 8KB |
| `create_project`  | 脚手架生成 `java-maven` / `python` / `node` 项目  | PathGuard 限制在项目根目录内；目标目录已存在则拒绝              |
| `web_search`      | 调用智谱 Web Search（兼容 GLM）                   | 屏蔽私网 IP / 回环 / `file://`；限速 30 次/分钟                |

---

## 4. 快速开始

### 4.1 前置条件

- Java 17+
- Maven 3.6+
- 一个支持工具调用的 OpenAI 兼容 LLM 端点。默认配置指向
  `https://open.bigmodel.cn/api/coding/paas/v4`，模型 `glm-5.1`。

### 4.2 配置 API Key

复制示例文件并编辑：

```bash
cp .env.example .env
# 然后编辑 .env，把 NOVAAGENT_API_KEY 填进去（可选填 NOVAAGENT_BASE_URL、NOVAAGENT_MODEL）
```

也可以直接用环境变量：

```bash
export NOVAAGENT_API_KEY=your_api_key_here
export NOVAAGENT_BASE_URL=https://open.bigmodel.cn/api/coding/paas/v4
export NOVAAGENT_MODEL=glm-5.1
```

还可以用 `~/.novaagent/config.properties` 放非密钥性的覆盖项：

```properties
api.baseUrl=https://api.deepseek.com/v1
api.model=deepseek-chat
```

如果 `NOVAAGENT_API_KEY` 没配，NovaAgent **仍会启动**，你可以执行
`/help`、`/config` 等命令 —— 只有走 ReAct 的对话会失败，并给出清晰的报错。

### 4.3 编译与运行

```bash
mvn clean package                                  # 产物：target/novaagent-0.1.0.jar
java -jar target/novaagent-0.1.0.jar
```

也可以直接从源码运行：

```bash
mvn clean compile exec:java -Dexec.mainClass="com.novaagent.cli.Main"
```

### 4.4 第一次对话

```
❯ 你好，请列出当前目录的文件
❯ 帮我创建一个 Java 项目叫 myapp
❯ 在 src/ 里搜索包含 TODO 的行
❯ 给我搜一下今天 AI 编程助手的趋势
```

### 4.5 斜杠命令

- `/help` — 显示命令列表
- `/tools` — 列出已注册的工具
- `/tools <name>` — 显示某个工具的参数 schema
- `/config` — 显示当前配置（API key 会脱敏）
- `/model <name>` — 切换模型（Phase 1：需要重启）
- `/clear` — 清屏
- `/exit` / `/quit` — 退出

---

## 5. 项目结构

```
src/main/java/com/novaagent
├── agent/                  # ReAct agent + 系统提示词加载器
│   ├── Agent.java
│   └── SystemPrompt.java
├── cli/                    # JLine REPL + 斜杠命令解析器
│   ├── Main.java
│   └── CliCommandParser.java
├── llm/                    # OpenAI Chat Completions 客户端 + DTO
│   ├── ChatCompletionResult.java
│   ├── ChatMessage.java
│   ├── ChatMessageSerializer.java
│   ├── LlmClient.java
│   ├── LlmException.java
│   ├── OpenAiCompatibleClient.java
│   └── ToolSpec.java
├── tool/                   # 8 个工具（见上表）+ Tool / ToolRegistry / PathGuard
│   ├── CreateProjectTool.java
│   ├── ExecuteCommandTool.java
│   ├── GlobFilesTool.java
│   ├── GrepCodeTool.java
│   ├── ListDirTool.java
│   ├── PathGuard.java
│   ├── ReadFileTool.java
│   ├── Tool.java
│   ├── ToolRegistry.java
│   ├── WebSearchTool.java
│   └── WriteFileTool.java
└── util/                   # Banner + ANSI 助手 + Markdown 渲染器 + AppConfig
    ├── Ansi.java
    ├── AnsiSupport.java
    ├── AppConfig.java
    ├── Banner.java
    └── MarkdownRenderer.java

src/main/resources
├── application.properties          # 默认配置（被用户配置和环境变量覆盖）
└── prompts/phase-1/system.md       # 默认系统提示词
```

---

## 6. 配置参考

| 配置项                       | 环境变量                   | 默认值                                          |
| ---------------------------- | -------------------------- | ----------------------------------------------- |
| `api.key`                    | `NOVAAGENT_API_KEY`        | （ReAct 必填）                                  |
| `api.baseUrl`                | `NOVAAGENT_BASE_URL`       | `https://open.bigmodel.cn/api/coding/paas/v4`   |
| `api.model`                  | `NOVAAGENT_MODEL`          | `glm-5.1`                                       |
| `websearch.url`              | `NOVAAGENT_WEBSEARCH_URL`  | `https://open.bigmodel.cn/api/paas/v4/web_search`|
| `websearch.key`              | `NOVAAGENT_WEBSEARCH_KEY`  | （回退到 `api.key`）                            |
| `agent.maxSteps`             | （无）                     | `8`                                             |
| `agent.maxToolOutputChars`   | （无）                     | `4000`                                          |
| `agent.requestTimeoutSeconds`| （无）                     | `60`                                            |

覆盖优先级：环境变量 > `~/.novaagent/config.properties` > `application.properties`。

---

## 7. Phase 1 → 下一期

当你熟悉 Phase 1 后，路线图上的下一批里程碑：

- **Phase 2** — Plan-and-Execute + DAG，提供 `/plan <task>` 命令
- **Phase 3** — 长期记忆 + 自动摘要
- **Phase 4** — RAG / 代码 embedding
- **Phase 7** — 单轮 LLM 回复内的并行工具调用
- **Phase 8** — `/model` 跨 provider 运行时切换
- ……一直到 Phase 23（微信 iLink 通道）

每一期都保留同一套 ReAct 内核，往上叠加新能力。