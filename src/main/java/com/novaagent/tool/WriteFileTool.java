package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class WriteFileTool implements Tool {

    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final PathGuard guard;

    public WriteFileTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Overwrite a file inside the project root with the provided text.",
            List.of(
                PathGuard.Param.required("path", "string", "Path relative to project root"),
                PathGuard.Param.required("content", "string", "Full text to write")
            ));
        return new ToolSpec.FunctionSpec("write_file",
            "Write text content to a file inside the project root (overwrites if exists).",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object pathObj = args.get("path");
        Object contentObj = args.get("content");
        if (pathObj == null || contentObj == null) return "missing required argument: path / content";
        String content = contentObj.toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return "content exceeds 5MB cap; refused.";
        PathGuard.Result res = guard.resolveForWrite(pathObj.toString());
        if (!res.ok()) return res.error();
        try {
            Files.writeString(res.path(), content, StandardCharsets.UTF_8);
            return "OK: wrote " + res.path() + " (" + content.length() + " chars)";
        } catch (IOException e) {
            return "write failed: " + e.getMessage();
        }
    }
}