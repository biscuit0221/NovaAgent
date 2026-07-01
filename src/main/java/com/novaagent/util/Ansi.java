package com.novaagent.util;

/**
 * Minimal ANSI color helper.
 */
public final class Ansi {

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";

    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";

    private Ansi() {}

    public static String paint(String color, String text) {
        if (!AnsiSupport.enabled() || text == null) return text;
        return color + text + RESET;
    }

    public static String bold(String text) { return paint(BOLD, text); }
    public static String dim(String text) { return paint(DIM, text); }
    public static String cyan(String text) { return paint(CYAN, text); }
    public static String green(String text) { return paint(GREEN, text); }
    public static String yellow(String text) { return paint(YELLOW, text); }
    public static String red(String text) { return paint(RED, text); }
    public static String magenta(String text) { return paint(MAGENTA, text); }
    public static String brightCyan(String text) { return paint(BRIGHT_CYAN, text); }
    public static String brightBlack(String text) { return paint(BRIGHT_BLACK, text); }
}