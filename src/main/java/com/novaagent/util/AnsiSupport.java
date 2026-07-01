package com.novaagent.util;

/**
 * Detects whether the current stdout should emit ANSI escape codes.
 *
 * Rules:
 *   1. NO_COLOR env var -> disable.
 *   2. Not a TTY (System.console() == null) -> disable.
 *   3. Windows: only emit when WT_SESSION / ANSICON / TERM_PROGRAM=vscode
 *      indicates a modern virtual terminal; default is conservative.
 */
public final class AnsiSupport {

    private static final boolean ENABLED = detect();

    private AnsiSupport() {}

    public static boolean enabled() { return ENABLED; }

    private static boolean detect() {
        String noColor = System.getenv("NO_COLOR");
        if (noColor != null && !noColor.isBlank()) return false;
        if (System.console() == null) return false;
        String term = System.getenv("TERM");
        if (term != null && term.equalsIgnoreCase("dumb")) return false;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String wt = System.getenv("WT_SESSION");
            String ansi = System.getenv("ANSICON");
            String termProgram = System.getenv("TERM_PROGRAM");
            if (wt != null || ansi != null || "vscode".equalsIgnoreCase(termProgram)) {
                return true;
            }
            return false;
        }
        return true;
    }
}