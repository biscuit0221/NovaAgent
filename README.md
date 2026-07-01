# NovaAgent · Phase 1

> A minimal Java Agent CLI — ReAct (Think · Act · Observe) single-loop, inspired by
> the first phase of *PaiCLI*. This README is for the **Phase 1 milestone only** —
> there are 23+ phases to come.

---

## 1. What is Phase 1?

NovaAgent is born from a learning project: rebuilding a mature Java Agent CLI from
scratch, absorbing one phase at a time. Phase 1 deliberately keeps the surface area
as small as possible while still demonstrating the **end-to-end** flow that all
later phases will extend.

**Phase 1 capabilities**

| Capability             | Detail                                                             |
| ---------------------- | ------------------------------------------------------------------ |
| ReAct Agent loop       | `Think → Act → Observe`, capped at `agent.maxSteps` (default 8)    |
| LLM client             | OpenAI Chat Completions protocol — works with GLM / DeepSeek / Kimi / StepFun / any compatible endpoint |
| Streaming              | SSE parsing of `delta.content`; CLI prints tokens as they arrive   |
| Tools (7 + 1)          | `read_file`, `write_file`, `list_dir`, `glob_files`, `grep_code`, `execute_command`, `create_project`, `web_search` |
| Project-root safety    | All file tools refuse to leave the project root (PathGuard)        |
| Markdown-aware CLI     | Banner, colors (auto off when not a TTY / `NO_COLOR`), table-free Markdown renderer |
| Interactive REPL       | JLine 3 with line editing, history, slash commands                 |
| Config layering        | `application.properties` → `~/.novaagent/config.properties` → env vars |
| Persistence-ready      | Per-session chat history is built up in memory; ready for export in later phases |

**Explicitly NOT in Phase 1**

- No plan-and-execute / DAG (Phase 2)
- No long-term memory or auto-summary (Phase 3)
- No RAG embeddings (Phase 4)
- No multi-agent roles (Phase 5)
- No HITL / approval flow (Phase 6)
- No parallel tool calls (Phase 7) — single-call per turn today
- No multi-model switching at runtime (Phase 8 partial; `api.model` switch requires restart)
- No MCP integration (Phase 10)
- No `web_fetch` (planned Phase 9)

---

## 2. How Phase 1 implements ReAct

```
┌────────────┐                              ┌────────────┐
│  CLI REPL  │  user input (raw)            │  ToolLayer │
│ (JLine 3)  │ ───────────────────────────▶ │ (Registry) │
└─────┬──────┘                              └─────┬──────┘
      │                                          │
      │ system + history + tools schema          │ invoke(name, args)
      ▼                                          │
┌────────────┐    assistant content              ▼
│  LLM       │ ◀── (or tool_calls)────────  Tool Result (string)
│  (OpenAI   │
│ compatible │    tool_results role=tool  ────▶ history grows
│   HTTP)    │
└────────────┘
      │
      │ when finish_reason = "stop" → print final markdown
      ▼
  Back to REPL
```

The ReAct loop is implemented in `com.novaagent.agent.Agent`. Each step:

1. Sends full history + tool specs to the LLM.
2. If the LLM returns tool calls, executes them sequentially (parallel is Phase 7).
3. Adds tool results as `role=tool` messages so the next LLM turn sees them.
4. Stops when the LLM returns `finish_reason=stop` or step cap is reached.

The system prompt lives at `src/main/resources/prompts/phase-1/system.md` and can
be overridden by `~/.novaagent/prompts/phase-1/system.md` (user-level) or
`<project>/.novaagent/prompts/phase-1/system.md` (project-level, takes priority).

---

## 3. Tools in Phase 1

All tools implement `com.novaagent.tool.Tool`, which has two methods:

```java
ToolSpec.FunctionSpec spec();    // name + description + JSON Schema
String invoke(Map<String,Object> args); // returns plain text (success or error)
```

| Tool             | Purpose                                                  | Safety                                                 |
| ---------------- | -------------------------------------------------------- | ------------------------------------------------------ |
| `read_file`      | Read a text file, capped at 1 MB                         | PathGuard inside project root                          |
| `write_file`     | Overwrite a text file, capped at 5 MB                    | PathGuard inside project root                          |
| `list_dir`       | List entries under a directory (recursive optional)      | PathGuard; skips `target/`, `node_modules/`, `.git/`... |
| `glob_files`     | Match by glob (`src/**/*.java`, …)                       | PathGuard; same skip list                              |
| `grep_code`      | Regex/substring search over code-like files             | PathGuard; skips build dirs                            |
| `execute_command`| Run a shell command in the project root                  | Blacklist (`rm -rf /`, `mkfs`, fork bomb, shutdown…); 60s default timeout, 8 KB output cap |
| `create_project` | Scaffold a `java-maven` / `python` / `node` project     | PathGuard inside project root; refuses existing dir    |
| `web_search`     | Search via Zhipu Web Search (GLM-compatible)             | Block private IPs / loopback / file:// ; 30 req/min rate limit |

---

## 4. Quick start

### 4.1 Prerequisites

- Java 17+
- Maven 3.6+
- An OpenAI-compatible LLM endpoint that supports tool calling. Default config
  points to `https://open.bigmodel.cn/api/coding/paas/v4` with model `glm-5.1`.

### 4.2 Configure an API key

Copy the example file and edit it:

```bash
cp .env.example .env
# then edit .env to set NOVAAGENT_API_KEY=... (and optionally NOVAAGENT_BASE_URL, NOVAAGENT_MODEL)
```

Or set environment variables directly:

```bash
export NOVAAGENT_API_KEY=your_api_key_here
export NOVAAGENT_BASE_URL=https://open.bigmodel.cn/api/coding/paas/v4
export NOVAAGENT_MODEL=glm-5.1
```

You can also use `~/.novaagent/config.properties` for non-secret overrides:

```properties
api.baseUrl=https://api.deepseek.com/v1
api.model=deepseek-chat
```

If `NOVAAGENT_API_KEY` is missing, NovaAgent will still start and let you run
`/help`, `/config`, etc. — only ReAct conversations will fail with a clear
error message.

### 4.3 Build & run

```bash
mvn clean package                                  # produces target/novaagent-0.1.0.jar
java -jar target/novaagent-0.1.0.jar
```

Or run directly from source:

```bash
mvn clean compile exec:java -Dexec.mainClass="com.novaagent.cli.Main"
```

### 4.4 First conversation

```
❯ 你好，请列出当前目录的文件
❯ 帮我创建一个 Java 项目叫 myapp
❯ 在 src/ 里搜索包含 TODO 的行
❯ 给我搜一下今天 AI 编程助手的趋势
```

### 4.5 Slash commands

- `/help` — show command list
- `/tools` — list registered tools
- `/tools <name>` — show a tool's schema
- `/config` — show current configuration (API key masked)
- `/model <name>` — switch model (Phase 1: requires restart)
- `/clear` — clear the screen
- `/exit` / `/quit` — quit

---

## 5. Project structure

```
src/main/java/com/novaagent
├── agent/                  # ReAct agent + system prompt loader
│   ├── Agent.java
│   └── SystemPrompt.java
├── cli/                    # JLine REPL + slash command parser
│   ├── Main.java
│   └── CliCommandParser.java
├── llm/                    # OpenAI Chat Completions client + DTOs
│   ├── ChatCompletionResult.java
│   ├── ChatMessage.java
│   ├── ChatMessageSerializer.java
│   ├── LlmClient.java
│   ├── LlmException.java
│   ├── OpenAiCompatibleClient.java
│   └── ToolSpec.java
├── tool/                   # 8 tools (see table above) + Tool/ToolRegistry/PathGuard
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
└── util/                   # Banner + ANSI helpers + Markdown renderer + AppConfig
    ├── Ansi.java
    ├── AnsiSupport.java
    ├── AppConfig.java
    ├── Banner.java
    └── MarkdownRenderer.java

src/main/resources
├── application.properties          # default config (overridden by user config + env)
└── prompts/phase-1/system.md       # default system prompt
```

---

## 6. Configuration reference

| Property                          | Env var                  | Default                                          |
| --------------------------------- | ------------------------ | ------------------------------------------------ |
| `api.key`                         | `NOVAAGENT_API_KEY`      | (required for ReAct)                             |
| `api.baseUrl`                     | `NOVAAGENT_BASE_URL`     | `https://open.bigmodel.cn/api/coding/paas/v4`    |
| `api.model`                       | `NOVAAGENT_MODEL`        | `glm-5.1`                                        |
| `websearch.url`                   | `NOVAAGENT_WEBSEARCH_URL`| `https://open.bigmodel.cn/api/paas/v4/web_search`|
| `websearch.key`                   | `NOVAAGENT_WEBSEARCH_KEY`| (falls back to `api.key`)                        |
| `agent.maxSteps`                  | (none)                   | `8`                                              |
| `agent.maxToolOutputChars`        | (none)                   | `4000`                                           |
| `agent.requestTimeoutSeconds`     | (none)                   | `60`                                             |

Override priority: env var > `~/.novaagent/config.properties` > `application.properties`.

---

## 7. Phase 1 → next

When you're comfortable with Phase 1, the next milestones in the planned roadmap are:

- **Phase 2** – Plan-and-Execute + DAG with `/plan <task>`
- **Phase 3** – Long-term memory + auto-summary
- **Phase 4** – RAG / code embeddings
- **Phase 7** – Parallel tool calls inside one LLM turn
- **Phase 8** – `/model` runtime switching across providers
- … and so on, up to Phase 23 (WeChat iLink channel)

Each phase will keep the same ReAct core, layering capabilities on top.
