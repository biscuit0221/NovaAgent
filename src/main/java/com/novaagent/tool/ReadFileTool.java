package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class ReadFileTool implements Tool {

    private static final long MAX_BYTES = 1024L * 1024L;
    private final PathGuard guard;

    public ReadFileTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Read a text file's content from inside the project root.",
            List.of(
                PathGuard.Param.required("path", "string", "Path relative to project root, e.g. src/main/java/Foo.java"),
                PathGuard.Param.optional("max_lines", "integer", "Only read the first N lines; defaults to whole file (still capped at 1MB)")
            ));
        return new ToolSpec.FunctionSpec("read_file",
            "Read the contents of a text file inside the project root.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        PathGuard.Result res = guard.resolveArg(args, "path", PathGuard.Expect.FILE);
        if (!res.ok()) return res.error();
        try {
            byte[] bytes = Files.readAllBytes(res.path());
            if (bytes.length > MAX_BYTES) {
                return "file too large (" + bytes.length + " bytes > " + MAX_BYTES + "); pass max_lines or use another tool.";
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            Object maxLinesObj = args.get("max_lines");
            if (maxLinesObj != null) {
                int maxLines = ((Number) maxLinesObj).intValue();
                String[] lines = content.split("\\R", -1);
                if (lines.length > maxLines) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < maxLines; i++) sb.append(lines[i]).append('\n');
                    sb.append("... [total ").append(lines.length).append(" lines; truncated to first ")
                        .append(maxLines).append("]\n");
                    return sb.toString();
                }
                return content;
            }
            return content;
        } catch (IOException e) {
            return "read failed: " + e.getMessage();
        }
    }
}