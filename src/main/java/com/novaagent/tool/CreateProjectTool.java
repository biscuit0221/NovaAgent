package com.novaagent.tool;

import com.novaagent.llm.ToolSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CreateProjectTool implements Tool {

    private final PathGuard guard;

    public CreateProjectTool(PathGuard guard) { this.guard = guard; }

    @Override
    public ToolSpec.FunctionSpec spec() {
        Map<String, Object> schema = PathGuard.schema(
            "Scaffold a project under the project root from a template.",
            List.of(
                PathGuard.Param.required("name", "string", "Project name; becomes the subdirectory name"),
                PathGuard.Param.required("template", "string", "Template: java-maven / python / node"),
                PathGuard.Param.optional("description", "string", "Optional description for README.md")
            ));
        return new ToolSpec.FunctionSpec("create_project",
            "Scaffold a java-maven / python / node project under the project root.",
            schema);
    }

    @Override
    public String invoke(Map<String, Object> args) {
        Object nameObj = args.get("name");
        Object templateObj = args.get("template");
        if (nameObj == null || templateObj == null) return "missing required argument: name / template";
        String name = nameObj.toString().trim();
        String template = templateObj.toString().trim();
        if (name.isBlank() || !name.matches("[a-zA-Z0-9_\\-\\.]{1,64}")) {
            return "invalid project name: " + name;
        }
        Path baseDir = guard.projectRoot().resolve(name).normalize();
        if (!baseDir.startsWith(guard.projectRoot())) return "project path escapes project root";
        if (Files.exists(baseDir)) return "directory already exists, will not overwrite: " + baseDir;
        try {
            switch (template) {
                case "java-maven" -> writeJavaMaven(baseDir, name, asString(args.get("description")));
                case "python"    -> writePython(baseDir, name, asString(args.get("description")));
                case "node"      -> writeNode(baseDir, name, asString(args.get("description")));
                default          -> { return "unknown template: " + template + " (java-maven / python / node)"; }
            }
            return "OK: created project " + name + " (template " + template + ") at " + baseDir;
        } catch (IOException e) {
            return "create failed: " + e.getMessage();
        }
    }

    private static String asString(Object o) {
        if (o == null) return "";
        String s = o.toString();
        return s.isBlank() ? "" : s;
    }

    private void writeJavaMaven(Path baseDir, String name, String desc) throws IOException {
        Files.createDirectories(baseDir.resolve("src/main/java"));
        Files.createDirectories(baseDir.resolve("src/test/java"));
        Files.createDirectories(baseDir.resolve("src/main/resources"));
        writeFile(baseDir.resolve("pom.xml"), pomXml(name));
        writeFile(baseDir.resolve("README.md"), readme(name, desc, "Maven"));
        writeFile(baseDir.resolve(".gitignore"), "target/\n.idea/\n.vscode/\n*.class\n");
        writeFile(baseDir.resolve("src/main/java/" + toJavaPath(name) + "/App.java"),
            "public class App {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello " + name + "!\");\n" +
            "    }\n" +
            "}\n");
    }

    private void writePython(Path baseDir, String name, String desc) throws IOException {
        Files.createDirectories(baseDir.resolve("src"));
        writeFile(baseDir.resolve("src/app.py"),
            "def main():\n" +
            "    print(\"Hello " + name + "!\")\n\n\n" +
            "if __name__ == \"__main__\":\n" +
            "    main()\n");
        writeFile(baseDir.resolve("README.md"), readme(name, desc, "Python"));
        writeFile(baseDir.resolve(".gitignore"), "__pycache__/\n.venv/\n*.pyc\n");
        writeFile(baseDir.resolve("requirements.txt"), "# add deps here\n");
    }

    private void writeNode(Path baseDir, String name, String desc) throws IOException {
        Files.createDirectories(baseDir.resolve("src"));
        String pkgJson = "{\n" +
            "  \"name\": \"" + name + "\",\n" +
            "  \"version\": \"0.1.0\",\n" +
            "  \"description\": \"" + desc.replace("\"", "\\\"") + "\",\n" +
            "  \"main\": \"src/index.js\",\n" +
            "  \"scripts\": { \"start\": \"node src/index.js\" },\n" +
            "  \"license\": \"MIT\"\n" +
            "}\n";
        writeFile(baseDir.resolve("package.json"), pkgJson);
        writeFile(baseDir.resolve("src/index.js"),
            "function main() {\n" +
            "  console.log('Hello " + name + "!');\n" +
            "}\n\n" +
            "main();\n");
        writeFile(baseDir.resolve("README.md"), readme(name, desc, "Node"));
        writeFile(baseDir.resolve(".gitignore"), "node_modules/\n");
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private static String pomXml(String name) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.1.0-SNAPSHOT</version>
                    <packaging>jar</packaging>
                    <properties>
                        <maven.compiler.source>17</maven.compiler.source>
                        <maven.compiler.target>17</maven.compiler.target>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-jar-plugin</artifactId>
                                <version>3.4.1</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(name);
    }

    private static String readme(String name, String desc, String kind) {
        if (desc == null || desc.isBlank()) desc = "A " + kind + " project scaffolded by NovaAgent.";
        return "# " + name + "\n\n" + desc + "\n\n## Build\n\nSee project tooling (" + kind + ").\n";
    }

    private static String toJavaPath(String name) {
        String[] parts = name.replace('-', '_').split("[^A-Za-z0-9_]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(p.toLowerCase());
        }
        return sb.length() == 0 ? "app" : sb.toString();
    }
}