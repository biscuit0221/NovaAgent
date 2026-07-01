package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ListDirTool implements Tool {

    private static final List<String> SKIP_DIRS = List.of(
        "target", "node_modules", ".git", ".idea", ".vscode", "build", "dist", "out");

    private final PathGuard guard;

    public ListDirTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "List entries under a directory inside the project root.",
            List.of(
                PathGuard.Param.optional("path", "string", "Directory path relative to project root; defaults to project root"),
                PathGuard.Param.optional("recursive", "boolean", "Walk sub-directories; defaults to false")
            ));
        return new ToolSpec.FunctionSpec("list_dir",
            "List immediate (or recursive) entries under a directory.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object pathObj = args.get("path");
        boolean recursive = Boolean.TRUE.equals(args.get("recursive"));
        PathGuard.Result res;
        if (pathObj == null || pathObj.toString().isBlank()) {
            res = PathGuard.Result.ok(guard.projectRoot());
        } else {
            res = guard.resolve(pathObj.toString(), PathGuard.Expect.DIR);
        }
        if (!res.ok()) return res.error();
        try {
            StringBuilder out = new StringBuilder();
            out.append("Directory: ").append(res.path()).append('\n');
            if (!recursive) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(res.path())) {
                    List<Path> entries = new ArrayList<>();
                    for (Path p : ds) entries.add(p);
                    entries.sort(Comparator.comparing(p -> p.getFileName().toString()));
                    for (Path p : entries) appendLine(out, p);
                }
            } else {
                try (Stream<Path> walk = Files.walk(res.path())) {
                    List<Path> all = new ArrayList<>();
                    walk.forEach(p -> {
                        Path parent = p.getParent();
                        if (parent != null) {
                            for (String skip : SKIP_DIRS) {
                                if (parent.getFileName() != null
                                    && parent.getFileName().toString().equals(skip)) return;
                            }
                        }
                        all.add(p);
                    });
                    all.sort(Comparator.comparing(Path::toString));
                    for (Path p : all) appendLine(out, p);
                }
            }
            return out.toString();
        } catch (IOException e) {
            return "list failed: " + e.getMessage();
        }
    }

    private void appendLine(StringBuilder out, Path p) {
        String name = p.getFileName() == null ? p.toString() : p.getFileName().toString();
        if (Files.isDirectory(p)) {
            out.append("  d                       ").append(name).append('/').append('\n');
        } else if (Files.isRegularFile(p)) {
            long size;
            try { size = Files.size(p); } catch (IOException e) { size = -1; }
            out.append("  f  ").append(String.format("%10d", size)).append("  ").append(name).append('\n');
        } else {
            out.append("  ?                       ").append(name).append('\n');
        }
    }
}