package com.novaagent.cli;

import com.novaagent.agent.Agent;
import com.novaagent.agent.SystemPrompt;
import com.novaagent.llm.LlmClient;
import com.novaagent.llm.OpenAiCompatibleClient;
import com.novaagent.llm.ToolSpec;
import com.novaagent.tool.CreateProjectTool;
import com.novaagent.tool.ExecuteCommandTool;
import com.novaagent.tool.GlobFilesTool;
import com.novaagent.tool.GrepCodeTool;
import com.novaagent.tool.ListDirTool;
import com.novaagent.tool.PathGuard;
import com.novaagent.tool.ReadFileTool;
import com.novaagent.tool.ToolRegistry;
import com.novaagent.tool.WebSearchTool;
import com.novaagent.tool.WriteFileTool;
import com.novaagent.util.Ansi;
import com.novaagent.util.AppConfig;
import com.novaagent.util.Banner;
import com.novaagent.util.MarkdownRenderer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public final class Main {

    private Main() {}

    public static void main(String[] args) {
        AppConfig cfg = new AppConfig();
        LlmClient llm;
        try {
            llm = OpenAiCompatibleClient.newBuilder()
                .providerName("glm")
                .baseUrl(cfg.baseUrl())
                .model(cfg.model())
                .apiKey(cfg.requireApiKey())
                .build();
        } catch (IllegalStateException ex) {
            System.err.println("[novaagent] WARN: " + ex.getMessage());
            llm = null;
        }

        Path projectRoot = pickProjectRoot();
        PathGuard guard = new PathGuard(projectRoot);
        ToolRegistry tools = buildTools(guard, cfg);

        String systemPrompt = SystemPrompt.load(projectRoot.toString());

        Agent agent = (llm == null) ? null : new Agent(
                llm, tools, systemPrompt,
                Integer.parseInt(cfg.get("agent.maxSteps", "8")),
                Integer.parseInt(cfg.get("agent.maxToolOutputChars", "4000")));

        Banner.print(cfg.model(), "ReAct");

        if (args.length > 0) {
            String a = args[0];
            if ("--help".equals(a) || "-h".equals(a)) { printHelp(); return; }
            System.err.println("Unknown argument: " + a + " (NovaAgent Phase 1 only accepts -h / --help)");
            return;
        }

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("NovaAgent")
                .build();
            System.out.println(Ansi.dim("  workspace: " + projectRoot));
            System.out.println(Ansi.dim("  tools (" + tools.names().size() + "): " + String.join(", ", tools.names())));
            System.out.println(Ansi.dim("  /help for commands, /exit to quit"));
            System.out.println();

            while (true) {
                String prompt = Ansi.cyan(">") + " ";
                String line;
                try {
                    line = reader.readLine(prompt);
                } catch (org.jline.reader.UserInterruptException ex) {
                    System.out.println("^C");
                    continue;
                }
                if (line == null) {
                    System.out.println("Bye!");
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) continue;
                CliCommandParser.Parsed parsed = CliCommandParser.parse(line);
                switch (parsed.command) {
                    case HELP -> printHelp();
                    case TOOLS -> handleTools(tools, parsed.arg);
                    case CLEAR -> {
                        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                        terminal.flush();
                    }
                    case CONFIG -> handleConfig(cfg, llm, projectRoot);
                    case MODEL -> System.out.println("[novaagent] Phase 1 does not switch models at runtime. Edit ~/.novaagent/config.properties and restart.");
                    case EXIT, QUIT -> { System.out.println(Ansi.cyan("Bye!")); return; }
                    case UNKNOWN -> {
                        if (parsed.arg.isBlank()) {
                            System.out.println("unknown command: " + line);
                            printHelp();
                            break;
                        }
                        runAgent(agent, parsed.rawInput);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[novaagent] terminal init failed: " + e.getMessage());
        }
    }

    private static Path pickProjectRoot() {
        Path defaultRoot = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        String override = System.getProperty("novaagent.projectRoot");
        if (override != null && !override.isBlank()) {
            Path p = Paths.get(override).toAbsolutePath();
            if (Files.exists(p)) return p.normalize();
        }
        return defaultRoot.normalize();
    }

    private static ToolRegistry buildTools(PathGuard guard, AppConfig cfg) {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ReadFileTool(guard));
        registry.register(new WriteFileTool(guard));
        registry.register(new ListDirTool(guard));
        registry.register(new GlobFilesTool(guard));
        registry.register(new GrepCodeTool(guard));
        registry.register(new ExecuteCommandTool(guard));
        registry.register(new CreateProjectTool(guard));
        String searchKey = cfg.webSearchKey();
        if (searchKey == null || searchKey.isBlank()) searchKey = cfg.get("api.key");
        registry.register(new WebSearchTool(searchKey, cfg.webSearchUrl()));
        return registry;
    }
    private static void handleTools(ToolRegistry tools, String arg) {
        if (arg == null || arg.isBlank()) {
            System.out.println("registered tools:");
            for (String name : tools.names()) {
                System.out.println("  - " + Ansi.cyan(name) + "  " + Ansi.dim(tools.get(name).spec().description()));
            }
            return;
        }
        if (!tools.contains(arg)) {
            System.out.println("unknown tool: " + arg);
            return;
        }
        ToolSpec.FunctionSpec spec = tools.get(arg).spec();
        System.out.println(Ansi.bold(spec.name()) + " - " + spec.description());
        System.out.println(Ansi.dim("params: ") + spec.parameters());
    }

    private static void handleConfig(AppConfig cfg, LlmClient llm, Path projectRoot) {
        System.out.println(Ansi.bold("NovaAgent configuration"));
        System.out.println("  base_url    : " + cfg.baseUrl());
        System.out.println("  model       : " + cfg.model());
        System.out.println("  api_key     : " + maskKey(cfg.get("api.key")));
        System.out.println("  websearch   : " + cfg.webSearchUrl());
        System.out.println("  workspace   : " + projectRoot);
        if (llm == null) System.out.println(Ansi.yellow("  ! LLM not initialized; ReAct commands will fail"));
    }

    private static String maskKey(String k) {
        if (k == null || k.isBlank()) return "(unset)";
        if (k.length() <= 4) return "****";
        return k.substring(0, 2) + "****" + k.substring(k.length() - 2);
    }
    private static void runAgent(Agent agent, String rawInput) {
        if (agent == null) {
            System.out.println(Ansi.red("[novaagent] Agent not ready: configure NOVAAGENT_API_KEY first."));
            return;
        }
        System.out.println(Ansi.dim("  > " + rawInput));
        System.out.println();
        StringBuilder streamedContent = new StringBuilder();
        List<com.novaagent.llm.ChatMessage> history = agent.run(rawInput, delta -> streamedContent.append(delta));
        StringBuilder finalAnswer = new StringBuilder();
        for (com.novaagent.llm.ChatMessage m : history) {
            if (com.novaagent.llm.ChatMessage.ROLE_ASSISTANT.equals(m.getRole())
                && m.getContent() instanceof String s
                && !s.isBlank()) {
                finalAnswer.append(s).append('\n');
            }
        }
        if (finalAnswer.length() == 0 && !streamedContent.isEmpty()) finalAnswer.append(streamedContent);
        if (finalAnswer.length() == 0) {
            System.out.println(Ansi.dim("  (empty response)"));
        } else {
            String rendered = MarkdownRenderer.render(finalAnswer.toString());
            for (String l : rendered.split("\\R", -1)) System.out.println("  " + l);
        }
        System.out.println();
    }

    private static void printHelp() {
        String help = Ansi.bold("NovaAgent · Phase 1 commands") + "\n"
            + "  /help            show this help\n"
            + "  /tools           list registered tools\n"
            + "  /tools <name>    show a tool's parameter schema\n"
            + "  /config          show current configuration (API key masked)\n"
            + "  /model <name>    switch model (Phase 1: requires restart)\n"
            + "  /clear           clear the screen\n"
            + "  /exit | /quit    quit\n"
            + "\nAny other input is sent to the ReAct agent. Try:\n"
            + "  'list files in the current directory'\n"
            + "  'create a Java project called myapp'\n"
            + "  'search for TODO in src/'\n"
            + "  'search the latest AI programming assistant trends'";
        System.out.println(help);
    }
}