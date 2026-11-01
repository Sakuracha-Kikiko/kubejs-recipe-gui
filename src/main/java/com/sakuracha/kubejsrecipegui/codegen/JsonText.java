package com.sakuracha.kubejsrecipegui.codegen;

/**
 * 极简 JSON 文本工具：只负责转义与缩进美化，不解析内容。
 */
public final class JsonText {
    private JsonText() {
    }

    /** 把任意字符串转成带双引号的 JSON 字符串字面量。 */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /** 把紧凑 JSON 重新缩进成两格缩进的可读文本；非法输入按原样返回。 */
    public static String pretty(String compact) {
        StringBuilder out = new StringBuilder(compact.length() + 64);
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    out.append(c);
                    if (hasContent(compact, i + 1)) {
                        depth++;
                        newline(out, depth);
                    }
                }
                case '}', ']' -> {
                    if (hasContent(compact, i + 1) || endsWithContent(out)) {
                        depth = Math.max(0, depth - 1);
                        newline(out, depth);
                    }
                    out.append(c);
                }
                case ',' -> {
                    out.append(c);
                    newline(out, depth);
                }
                case ':' -> out.append(": ");
                case ' ', '\n', '\r', '\t' -> {
                }
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static boolean hasContent(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                return c != '}' && c != ']';
            }
        }
        return false;
    }

    private static boolean endsWithContent(StringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            char c = sb.charAt(i);
            if (c != ' ' && c != '\n') {
                return c != '{' && c != '[';
            }
        }
        return false;
    }

    private static void newline(StringBuilder out, int depth) {
        out.append('\n');
        out.append("  ".repeat(Math.max(0, depth)));
    }
}
