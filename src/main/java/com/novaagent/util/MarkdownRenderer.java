package com.novaagent.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight Markdown -> terminal renderer.
 * Supports fenced code blocks, headings, bullet/ordered lists,
 * inline code, bold, italic.
 */
public final class MarkdownRenderer {

    private static final Pattern FENCE = Pattern.compile("^```([a-zA-Z0-9_+\\-]*)");
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*]\\s+");
    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+)\\.\\s+");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*\\n]+)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");

    private MarkdownRenderer() {}

    public static String render(String md) {
        if (md == null || md.isEmpty()) return "";
        String[] lines = md.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            Matcher fence = FENCE.matcher(lines[i].trim());
            if (fence.matches()) {
                int end = findFenceEnd(lines, i + 1);
                appendCodeBlock(out, lines, i + 1, end, fence.group(1));
                i = end + 1;
                continue;
            }
            out.append(formatInline(lines[i])).append('\n');
            i++;
        }
        return trimTrailingBlankLines(out);
    }

    private static int findFenceEnd(String[] lines, int start) {
        for (int j = start; j < lines.length; j++) {
            if (lines[j].trim().equals("```")) return j;
        }
        return lines.length - 1;
    }

    private static void appendCodeBlock(StringBuilder out, String[] lines, int from, int to, String lang) {
        out.append(Ansi.dim("--- ")).append(Ansi.dim(lang == null || lang.isEmpty() ? "code" : lang)).append('\n');
        for (int k = from; k <= to; k++) {
            out.append(Ansi.brightBlack("  | ")).append(lines[k]).append('\n');
        }
    }

    private static String formatInline(String line) {
        if (line.isEmpty()) return "";
        Matcher h = HEADING.matcher(line);
        if (h.matches()) {
            int level = h.group(1).length();
            String title = h.group(2).trim();
            String prefix = level <= 2 ? Ansi.bold(Ansi.cyan(title)) : Ansi.bold(title);
            return "  " + prefix;
        }
        Matcher ol = ORDERED.matcher(line);
        if (ol.find()) {
            return ol.replaceFirst(ol.group(1) + Ansi.cyan(ol.group(2) + ".") + " ");
        }
        Matcher bl = BULLET.matcher(line);
        if (bl.find()) {
            return bl.replaceFirst(bl.group(1) + Ansi.cyan("-") + " ");
        }
        return inlineFormatting(line);
    }

    private static String inlineFormatting(String line) {
        String s = line;
        Matcher ic = INLINE_CODE.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (ic.find()) {
            ic.appendReplacement(sb, Matcher.quoteReplacement(Ansi.brightBlack("`" + ic.group(1) + "`")));
        }
        ic.appendTail(sb);
        s = sb.toString();
        Matcher b = BOLD.matcher(s);
        sb = new StringBuffer();
        while (b.find()) {
            b.appendReplacement(sb, Matcher.quoteReplacement(Ansi.bold(b.group(1))));
        }
        b.appendTail(sb);
        s = sb.toString();
        Matcher it = ITALIC.matcher(s);
        sb = new StringBuffer();
        while (it.find()) {
            it.appendReplacement(sb, Matcher.quoteReplacement(Ansi.dim(it.group(1))));
        }
        it.appendTail(sb);
        return s;
    }

    private static String trimTrailingBlankLines(StringBuilder sb) {
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '\n') end--;
        sb.setLength(end);
        return sb.toString();
    }

    public static List<String> toLines(String md) {
        List<String> lines = new ArrayList<>();
        for (String l : render(md).split("\\R", -1)) lines.add(l);
        return lines;
    }
}