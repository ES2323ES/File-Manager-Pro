package com.aishhatorah.filemanagerpro;

import android.graphics.Color;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * סעיפים 12, 13: מדגיש תחביר מהיר וייעודי ב-Java עבור 20 שפות תכנות וקבצי הגדרות,
 * כולל HTML משולב CSS+JS ו-Markdown Renderer מובנה.
 */
public final class CodeSyntaxHighlighter {

    public enum Language {
        PLAIN("טקסט רגיל"),
        TYPESCRIPT("TypeScript"),
        JAVASCRIPT("JavaScript"),
        PYTHON("Python"),
        SHELL("Shell / Bash"),
        JAVA("Java"),
        C_CPP("C / C++"),
        HTML("HTML / CSS / JS"),
        CSS("CSS / SCSS"),
        RUST("Rust"),
        BAT("Batch (.bat)"),
        KOTLIN("Kotlin"),
        PROPERTIES("Properties / INI"),
        SMALI("Smali"),
        PHP("PHP"),
        YAML("YAML"),
        JSON("JSON"),
        XML("XML / SVG"),
        SQL("SQL"),
        MARKDOWN("Markdown");

        public final String displayName;

        Language(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final int MAX_HIGHLIGHT_CHARS = 85_000;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?[fFlLuU]?)\\b");
    private static final Pattern STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`");
    private static final Pattern C_COMMENT_PATTERN = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern HASH_COMMENT_PATTERN = Pattern.compile("#[^\\n]*");
    private static final Pattern XML_COMMENT_PATTERN = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern XML_TAG_PATTERN = Pattern.compile("</?([a-zA-Z0-9_:\\-]+)");
    private static final Pattern XML_ATTR_PATTERN = Pattern.compile("\\s([a-zA-Z0-9_:\\-]+)=");
    private static final Pattern ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SMALI_DIRECTIVE = Pattern.compile("(^|\\s)\\.[a-zA-Z\\-]+");
    private static final Pattern SMALI_REG = Pattern.compile("\\b[vp]\\d+\\b");
    private static final Pattern YAML_KEY = Pattern.compile("(?m)^\\s*[A-Za-z0-9_.\\-]+(?=:)");
    private static final Pattern JSON_KEY = Pattern.compile("\"([^\"]+)\"\\s*:");
    private static final Pattern MD_HEADING = Pattern.compile("(?m)^#{1,6}\\s+.*$");
    private static final Pattern MD_CODE = Pattern.compile("```.*?```|`[^`]+`", Pattern.DOTALL);

    private static final Set<String> KEYWORDS_COMMON = new HashSet<>(Arrays.asList(
            "abstract", "as", "async", "await", "break", "case", "catch", "class", "const",
            "continue", "data", "def", "default", "do", "elif", "else", "enum", "export",
            "extends", "false", "final", "finally", "fn", "for", "from", "fun", "function",
            "if", "impl", "implements", "import", "in", "interface", "internal", "is", "lambda",
            "let", "loop", "match", "mut", "namespace", "new", "null", "None", "object",
            "override", "package", "private", "protected", "pub", "public", "raise", "return",
            "select", "self", "static", "struct", "super", "switch", "this", "throw", "trait",
            "true", "True", "False", "try", "type", "typeof", "use", "val", "var", "void",
            "while", "with", "yield", "echo", "fi", "then", "done", "esac", "local",
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "JOIN", "ON", "ORDER", "BY", "GROUP", "LIMIT", "AND", "OR", "NOT"
    ));

    private CodeSyntaxHighlighter() {
    }

    public static Language detectLanguage(String fileName) {
        if (fileName == null) return Language.PLAIN;
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return Language.TYPESCRIPT;
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return Language.JAVASCRIPT;
        if (lower.endsWith(".py")) return Language.PYTHON;
        if (lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")) return Language.SHELL;
        if (lower.endsWith(".java")) return Language.JAVA;
        if (lower.endsWith(".c") || lower.endsWith(".h") || lower.endsWith(".cpp") || lower.endsWith(".hpp") || lower.endsWith(".cc")) return Language.C_CPP;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return Language.HTML;
        if (lower.endsWith(".css") || lower.endsWith(".scss")) return Language.CSS;
        if (lower.endsWith(".rs")) return Language.RUST;
        if (lower.endsWith(".bat") || lower.endsWith(".cmd")) return Language.BAT;
        if (lower.endsWith(".kt") || lower.endsWith(".kts")) return Language.KOTLIN;
        if (lower.endsWith(".properties") || lower.endsWith(".prop") || lower.endsWith(".ini") || lower.endsWith(".cfg") || lower.endsWith(".conf")) return Language.PROPERTIES;
        if (lower.endsWith(".smali")) return Language.SMALI;
        if (lower.endsWith(".php")) return Language.PHP;
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return Language.YAML;
        if (lower.endsWith(".json")) return Language.JSON;
        if (lower.endsWith(".xml") || lower.endsWith(".svg")) return Language.XML;
        if (lower.endsWith(".sql")) return Language.SQL;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return Language.MARKDOWN;
        return Language.PLAIN;
    }

    public static void highlight(Spannable editable, Language lang, boolean darkBg) {
        if (editable == null || lang == null || lang == Language.PLAIN) return;
        int len = Math.min(editable.length(), MAX_HIGHLIGHT_CHARS);
        ForegroundColorSpan[] old = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
        for (ForegroundColorSpan s : old) editable.removeSpan(s);
        if (len == 0) return;

        CharSequence text = editable.subSequence(0, len);

        int colKeyword = darkBg ? Color.parseColor("#C084FC") : Color.parseColor("#7C3AED");
        int colString = darkBg ? Color.parseColor("#4ADE80") : Color.parseColor("#15803D");
        int colNumber = darkBg ? Color.parseColor("#FB923C") : Color.parseColor("#C2410C");
        int colComment = darkBg ? Color.parseColor("#64748B") : Color.parseColor("#6B7280");
        int colTag = darkBg ? Color.parseColor("#60A5FA") : Color.parseColor("#1D4ED8");
        int colAttr = darkBg ? Color.parseColor("#38BDF8") : Color.parseColor("#0284C7");
        int colAnno = darkBg ? Color.parseColor("#FBBF24") : Color.parseColor("#B45309");

        if (lang == Language.MARKDOWN) {
            applySpan(editable, MD_HEADING.matcher(text), colTag, 0);
            applySpan(editable, MD_CODE.matcher(text), colString, 0);
            return;
        }

        // מספרים ומחרוזות
        applySpan(editable, NUMBER_PATTERN.matcher(text), colNumber, 0);

        if (lang == Language.XML || lang == Language.HTML) {
            applySpan(editable, XML_TAG_PATTERN.matcher(text), colTag, 1);
            applySpan(editable, XML_ATTR_PATTERN.matcher(text), colAttr, 1);
            applySpan(editable, STRING_PATTERN.matcher(text), colString, 0);
            if (lang == Language.HTML) {
                highlightWords(editable, text, colKeyword);
                applySpan(editable, C_COMMENT_PATTERN.matcher(text), colComment, 0);
            }
            applySpan(editable, XML_COMMENT_PATTERN.matcher(text), colComment, 0);
            return;
        }

        if (lang == Language.JSON) {
            applySpan(editable, STRING_PATTERN.matcher(text), colString, 0);
            applySpan(editable, JSON_KEY.matcher(text), colTag, 1);
            return;
        }

        if (lang == Language.YAML || lang == Language.PROPERTIES) {
            applySpan(editable, YAML_KEY.matcher(text), colTag, 0);
            applySpan(editable, STRING_PATTERN.matcher(text), colString, 0);
            applySpan(editable, HASH_COMMENT_PATTERN.matcher(text), colComment, 0);
            return;
        }

        if (lang == Language.SMALI) {
            applySpan(editable, SMALI_DIRECTIVE.matcher(text), colKeyword, 0);
            applySpan(editable, SMALI_REG.matcher(text), colAttr, 0);
            applySpan(editable, STRING_PATTERN.matcher(text), colString, 0);
            applySpan(editable, HASH_COMMENT_PATTERN.matcher(text), colComment, 0);
            return;
        }

        // שפות C-style / Python / Shell / SQL / PHP
        highlightWords(editable, text, colKeyword);
        applySpan(editable, ANNOTATION_PATTERN.matcher(text), colAnno, 0);
        applySpan(editable, STRING_PATTERN.matcher(text), colString, 0);

        if (lang == Language.PYTHON || lang == Language.SHELL || lang == Language.PHP) {
            applySpan(editable, HASH_COMMENT_PATTERN.matcher(text), colComment, 0);
        }
        if (lang != Language.PYTHON && lang != Language.SHELL) {
            applySpan(editable, C_COMMENT_PATTERN.matcher(text), colComment, 0);
        }
        if (lang == Language.SQL) {
            applySpan(editable, Pattern.compile("--[^\\n]*").matcher(text), colComment, 0);
        }
        if (lang == Language.BAT) {
            applySpan(editable, Pattern.compile("(?im)^\\s*(?:REM|::).*$").matcher(text), colComment, 0);
        }
    }

    private static void highlightWords(Spannable editable, CharSequence text, int color) {
        String s = text.toString();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                i++;
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) i++;
                String word = s.substring(start, i);
                if (KEYWORDS_COMMON.contains(word) || KEYWORDS_COMMON.contains(word.toUpperCase(Locale.ROOT))) {
                    editable.setSpan(new ForegroundColorSpan(color), start, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                i++;
            }
        }
    }

    private static void applySpan(Spannable editable, Matcher m, int color, int group) {
        while (m.find()) {
            int start = m.start(group);
            int end = m.end(group);
            if (start >= 0 && end > start) {
                editable.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    public static String markdownToHtml(String md, boolean darkBg, String textHex, String accentHex) {
        String bgHex = darkBg ? "#0F172A" : "#FFFFFF";
        String cardHex = darkBg ? "#1E293B" : "#F1F5F9";
        StringBuilder body = new StringBuilder();
        String[] lines = (md == null ? "" : md).replace("\r\n", "\n").split("\n");
        boolean inCode = false;
        boolean inList = false;

        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (!inCode) {
                    if (inList) { body.append("</ul>"); inList = false; }
                    body.append("<pre><code>");
                    inCode = true;
                } else {
                    body.append("</code></pre>");
                    inCode = false;
                }
                continue;
            }
            if (inCode) {
                body.append(escapeHtml(line)).append("\n");
                continue;
            }
            String trim = line.trim();
            if (trim.isEmpty()) {
                if (inList) { body.append("</ul>"); inList = false; }
                body.append("<br/>");
            } else if (trim.startsWith("### ")) {
                body.append("<h3>").append(inlineMd(trim.substring(4))).append("</h3>");
            } else if (trim.startsWith("## ")) {
                body.append("<h2>").append(inlineMd(trim.substring(3))).append("</h2>");
            } else if (trim.startsWith("# ")) {
                body.append("<h1>").append(inlineMd(trim.substring(2))).append("</h1>");
            } else if (trim.startsWith("- ") || trim.startsWith("* ")) {
                if (!inList) { body.append("<ul>"); inList = true; }
                body.append("<li>").append(inlineMd(trim.substring(2))).append("</li>");
            } else if (trim.startsWith("> ")) {
                body.append("<blockquote>").append(inlineMd(trim.substring(2))).append("</blockquote>");
            } else if ("---".equals(trim) || "***".equals(trim)) {
                body.append("<hr/>");
            } else {
                if (inList) { body.append("</ul>"); inList = false; }
                body.append("<p>").append(inlineMd(trim)).append("</p>");
            }
        }
        if (inList) body.append("</ul>");
        if (inCode) body.append("</code></pre>");

        return "<!DOCTYPE html><html dir='auto'><head><meta charset='utf-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<style>body{background:" + bgHex + ";color:" + textHex + ";font-family:sans-serif;padding:16px;line-height:1.65;}"
                + "h1,h2,h3{color:" + accentHex + ";margin:14px 0 8px;}code,pre{background:" + cardHex + ";border-radius:8px;}"
                + "pre{padding:12px;overflow-x:auto;}code{padding:2px 5px;}blockquote{border-inline-start:3px solid " + accentHex + ";margin:8px 0;padding:4px 12px;opacity:.9;}"
                + "</style></head><body>" + body + "</body></html>";
    }

    private static String inlineMd(String s) {
        String esc = escapeHtml(s);
        esc = esc.replaceAll("`([^`]+)`", "<code>$1</code>");
        esc = esc.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        esc = esc.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        esc = esc.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href='$2'>$1</a>");
        return esc;
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
