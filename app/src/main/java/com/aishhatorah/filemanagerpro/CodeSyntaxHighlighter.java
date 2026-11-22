package com.aishhatorah.filemanagerpro;

import android.graphics.Color;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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

    public static final class MarkdownRenderResult {
        public final String html;
        public final List<String> codeBlocks;

        public MarkdownRenderResult(String html, List<String> codeBlocks) {
            this.html = html;
            this.codeBlocks = codeBlocks;
        }
    }

    private static final Pattern HR_PATTERN = Pattern.compile("^\\s*(?:[-*_]\\s*){3,}$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+[.)]\\s+(.*)$");
    private static final Pattern TASK_LIST_PATTERN = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+)\\[([ xX])]\\s+(.*)$");
    private static final Pattern GH_ALERT_HEADER_PATTERN = Pattern.compile("^\\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)]\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\(([^)]+)\\)");

    public static String markdownToHtml(String md, boolean darkBg, String textHex, String accentHex) {
        return markdownToHtmlWithBlocks(md, darkBg, textHex, accentHex).html;
    }

    public static MarkdownRenderResult markdownToHtmlWithBlocks(String md, boolean darkBg, String textHex, String accentHex) {
        String bgHex = darkBg ? "#0B1220" : "#F8FAFC";
        String cardHex = darkBg ? "rgba(30,41,59,0.78)" : "rgba(255,255,255,0.88)";
        String cardBorder = darkBg ? "rgba(148,163,184,0.22)" : "rgba(15,23,42,0.10)";
        String codeBg = darkBg ? "rgba(15,23,42,0.92)" : "#F1F5F9";
        String codeHeadBg = darkBg ? "rgba(30,41,59,0.96)" : "#E2E8F0";
        String mutedHex = darkBg ? "#94A3B8" : "#64748B";
        String safeAccent = sanitizeCssColor(accentHex, "#38BDF8");
        String safeText = sanitizeCssColor(textHex, darkBg ? "#F1F5F9" : "#0F172A");

        StringBuilder body = new StringBuilder();
        List<String> codeBlocks = new ArrayList<>();
        String[] lines = (md == null ? "" : md).replace("\r\n", "\n").replace("\r", "\n").split("\n", -1);

        int idx = 0;
        while (idx < lines.length) {
            String rawLine = lines[idx];
            String trim = rawLine.trim();

            if (trim.isEmpty()) {
                idx++;
                continue;
            }

            // 1. Fenced code block (``` or ~~~)
            if (trim.startsWith("```") || trim.startsWith("~~~")) {
                String fence = trim.startsWith("~~~") ? "~~~" : "```";
                String langHint = trim.substring(3).trim();
                int spaceCut = langHint.indexOf(' ');
                if (spaceCut > 0) langHint = langHint.substring(0, spaceCut);
                StringBuilder codeRaw = new StringBuilder();
                idx++;
                while (idx < lines.length && !lines[idx].trim().startsWith(fence)) {
                    if (codeRaw.length() > 0) codeRaw.append('\n');
                    codeRaw.append(lines[idx]);
                    idx++;
                }
                if (idx < lines.length && lines[idx].trim().startsWith(fence)) {
                    idx++;
                }
                int blockIndex = codeBlocks.size();
                String codeStr = codeRaw.toString();
                codeBlocks.add(codeStr);
                appendCodeBubble(body, blockIndex, langHint, codeStr, darkBg);
                continue;
            }

            // 2. Horizontal rules (---, ***, ___, spaced)
            if (HR_PATTERN.matcher(trim).matches()) {
                body.append("<hr/>");
                idx++;
                continue;
            }

            // 3. ATX Headings (# .. ######)
            int hashCount = countHeadingHashes(trim);
            if (hashCount > 0) {
                String headingContent = trim.substring(hashCount).trim();
                body.append("<h").append(hashCount).append(">")
                        .append(inlineMd(headingContent))
                        .append("</h").append(hashCount).append(">");
                idx++;
                continue;
            }

            // 4. Blockquote & GitHub Alerts (> [!NOTE], > [!TIP], > [!IMPORTANT], > [!WARNING], > [!CAUTION])
            if (trim.startsWith(">")) {
                List<String> quoteLines = new ArrayList<>();
                while (idx < lines.length && lines[idx].trim().startsWith(">")) {
                    String ql = lines[idx].trim().substring(1);
                    if (ql.startsWith(" ")) ql = ql.substring(1);
                    quoteLines.add(ql);
                    idx++;
                }
                appendBlockquoteOrAlert(body, quoteLines);
                continue;
            }

            // 5. Tables (header | ... + separator |---|)
            if (isTableHeader(lines, idx)) {
                List<String[]> rows = new ArrayList<>();
                String[] headers = splitTableRow(lines[idx]);
                String[] alignments = parseTableAlignments(lines[idx + 1]);
                idx += 2;
                while (idx < lines.length && lines[idx].contains("|") && !lines[idx].trim().isEmpty()) {
                    rows.add(splitTableRow(lines[idx]));
                    idx++;
                }
                appendTable(body, headers, alignments, rows);
                continue;
            }

            // 6. Task list / Unordered list
            if (isUnorderedOrTaskLine(trim)) {
                body.append("<ul>");
                while (idx < lines.length) {
                    String t = lines[idx].trim();
                    if (!isUnorderedOrTaskLine(t)) break;
                    Matcher taskM = TASK_LIST_PATTERN.matcher(t);
                    if (taskM.matches()) {
                        boolean checked = "x".equalsIgnoreCase(taskM.group(1));
                        body.append("<li class='task-li'><span class='task-box")
                                .append(checked ? " checked'>✓" : "'>")
                                .append("</span><span>")
                                .append(inlineMd(taskM.group(2)))
                                .append("</span></li>");
                    } else {
                        body.append("<li>").append(inlineMd(t.substring(2).trim())).append("</li>");
                    }
                    idx++;
                }
                body.append("</ul>");
                continue;
            }

            // 7. Ordered list
            if (ORDERED_LIST_PATTERN.matcher(trim).matches()) {
                body.append("<ol>");
                while (idx < lines.length) {
                    String t = lines[idx].trim();
                    Matcher olM = ORDERED_LIST_PATTERN.matcher(t);
                    if (!olM.matches()) break;
                    body.append("<li>").append(inlineMd(olM.group(1))).append("</li>");
                    idx++;
                }
                body.append("</ol>");
                continue;
            }

            // 8. Paragraph (collect contiguous plain lines unless setext heading or block start)
            StringBuilder para = new StringBuilder(trim);
            idx++;
            while (idx < lines.length) {
                String nextRaw = lines[idx];
                String nextTrim = nextRaw.trim();
                if (nextTrim.isEmpty()) break;
                if (nextTrim.matches("^={3,}$")) {
                    body.append("<h1>").append(inlineMd(para.toString())).append("</h1>");
                    para.setLength(0);
                    idx++;
                    break;
                }
                if (nextTrim.matches("^-{3,}$") && !para.toString().contains("\n")) {
                    body.append("<h2>").append(inlineMd(para.toString())).append("</h2>");
                    para.setLength(0);
                    idx++;
                    break;
                }
                if (nextTrim.startsWith("```") || nextTrim.startsWith("~~~")
                        || countHeadingHashes(nextTrim) > 0
                        || HR_PATTERN.matcher(nextTrim).matches()
                        || nextTrim.startsWith(">")
                        || isUnorderedOrTaskLine(nextTrim)
                        || ORDERED_LIST_PATTERN.matcher(nextTrim).matches()
                        || isTableHeader(lines, idx)) {
                    break;
                }
                para.append(' ').append(nextTrim);
                idx++;
            }
            if (para.length() > 0) {
                body.append("<p>").append(inlineMd(para.toString())).append("</p>");
            }
        }

        String kwCol = darkBg ? "#C084FC" : "#7C3AED";
        String strCol = darkBg ? "#4ADE80" : "#15803D";
        String numCol = darkBg ? "#FB923C" : "#C2410C";
        String comCol = darkBg ? "#64748B" : "#6B7280";
        String tagCol = darkBg ? "#60A5FA" : "#1D4ED8";
        String annoCol = darkBg ? "#FBBF24" : "#B45309";
        String hrCol = darkBg ? "rgba(148,163,184,0.28)" : "rgba(15,23,42,0.14)";
        String tblAlt = darkBg ? "rgba(255,255,255,0.03)" : "rgba(15,23,42,0.025)";
        String inlineCodeBg = darkBg ? "rgba(148,163,184,0.16)" : "rgba(15,23,42,0.07)";

        String html = "<!DOCTYPE html><html dir='auto'><head><meta charset='utf-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'/>"
                + "<style>"
                + "*{box-sizing:border-box;}"
                + "body{background:" + bgHex + ";color:" + safeText + ";font-family:sans-serif;padding:16px 16px 28px;margin:0;line-height:1.7;word-wrap:break-word;}"
                + "h1,h2,h3,h4,h5,h6{color:" + safeAccent + ";margin:18px 0 8px;line-height:1.35;}"
                + "h1{font-size:1.55em;padding-bottom:6px;border-bottom:1px solid " + cardBorder + ";}"
                + "h2{font-size:1.3em;padding-bottom:4px;border-bottom:1px solid " + cardBorder + ";}"
                + "h3{font-size:1.12em;}"
                + "p{margin:8px 0;}"
                + "a{color:" + safeAccent + ";text-decoration:none;border-bottom:1px solid " + safeAccent + ";}"
                + "hr{border:0;height:1.5px;background:" + hrCol + ";margin:16px 0;border-radius:2px;}"
                + "ul,ol{margin:8px 0;padding-inline-start:22px;}"
                + "li{margin:4px 0;}"
                + "li.task-li{list-style:none;margin-inline-start:-16px;display:flex;align-items:flex-start;gap:8px;}"
                + ".task-box{width:17px;height:17px;border-radius:5px;border:1.5px solid " + mutedHex + ";display:inline-flex;align-items:center;justify-content:center;font-size:11px;font-weight:700;margin-top:4px;flex-shrink:0;color:#fff;}"
                + ".task-box.checked{background:" + safeAccent + ";border-color:" + safeAccent + ";}"
                + "code{background:" + inlineCodeBg + ";padding:2px 6px;border-radius:6px;font-family:monospace;font-size:0.9em;direction:ltr;unicode-bidi:embed;}"
                + "blockquote{background:" + cardHex + ";border:1px solid " + cardBorder + ";border-inline-start:3.5px solid " + safeAccent + ";border-radius:12px;margin:10px 0;padding:10px 14px;}"
                + "blockquote p{margin:3px 0;}"
                + ".code-bubble{background:" + codeBg + ";border:1px solid " + cardBorder + ";border-radius:14px;margin:12px 0;overflow:hidden;box-shadow:0 4px 14px rgba(0,0,0,0.08);}"
                + ".code-head{background:" + codeHeadBg + ";display:flex;align-items:center;justify-content:space-between;padding:7px 12px;border-bottom:1px solid " + cardBorder + ";}"
                + ".code-lang{font-family:monospace;font-size:11px;font-weight:700;color:" + mutedHex + ";text-transform:uppercase;letter-spacing:0.6px;}"
                + ".copy-btn{background:" + safeAccent + ";color:#fff;border:0;border-radius:9px;padding:4px 10px;font-size:11px;font-weight:700;cursor:pointer;}"
                + ".copy-btn:active{opacity:0.8;}"
                + ".code-pre{margin:0;padding:12px;overflow-x:auto;font-family:monospace;font-size:12.5px;line-height:1.55;direction:ltr;text-align:left;background:transparent;}"
                + ".code-pre code{background:transparent;padding:0;border-radius:0;font-size:inherit;}"
                + ".tok-kw{color:" + kwCol + ";font-weight:600;}.tok-str{color:" + strCol + ";}.tok-num{color:" + numCol + ";}"
                + ".tok-com{color:" + comCol + ";font-style:italic;}.tok-tag{color:" + tagCol + ";}.tok-anno{color:" + annoCol + ";}"
                + ".gh-alert{border-radius:14px;padding:11px 14px;margin:12px 0;border:1px solid " + cardBorder + ";border-inline-start:4px solid var(--al-col);background:" + cardHex + ";}"
                + ".gh-alert-title{display:flex;align-items:center;gap:7px;font-weight:700;font-size:13px;color:var(--al-col);margin-bottom:4px;}"
                + ".gh-alert-body p{margin:3px 0;}"
                + ".gh-note{--al-col:#3B82F6;}.gh-tip{--al-col:#10B981;}.gh-important{--al-col:#A855F7;}.gh-warning{--al-col:#F59E0B;}.gh-caution{--al-col:#EF4444;}"
                + ".table-wrap{overflow-x:auto;margin:12px 0;border-radius:12px;border:1px solid " + cardBorder + ";background:" + cardHex + ";}"
                + "table{width:100%;border-collapse:collapse;font-size:13.5px;}"
                + "th,td{padding:8px 11px;border-bottom:1px solid " + cardBorder + ";}"
                + "th{background:" + codeHeadBg + ";font-weight:700;color:" + safeAccent + ";}"
                + "tr:nth-child(even) td{background:" + tblAlt + ";}"
                + "img{max-width:100%;border-radius:10px;}"
                + ".badge-chip{display:inline-block;padding:2px 8px;border-radius:9px;background:" + inlineCodeBg + ";border:1px solid " + cardBorder + ";font-size:12px;}"
                + "</style>"
                + "<script>"
                + "function copyCodeBlock(idx,btn){try{if(window.MdBridge&&window.MdBridge.copyCode){window.MdBridge.copyCode(idx);if(btn){var prev=btn.textContent;btn.textContent='הועתק ✓';setTimeout(function(){btn.textContent=prev;},1400);}}}catch(e){}}"
                + "</script>"
                + "</head><body>" + body + "</body></html>";

        return new MarkdownRenderResult(html, codeBlocks);
    }

    private static int countHeadingHashes(String trim) {
        int count = 0;
        while (count < trim.length() && count < 6 && trim.charAt(count) == '#') {
            count++;
        }
        if (count > 0 && count < trim.length() && Character.isWhitespace(trim.charAt(count))) {
            return count;
        }
        return 0;
    }

    private static boolean isUnorderedOrTaskLine(String trim) {
        return trim.startsWith("- ") || trim.startsWith("* ") || trim.startsWith("+ ");
    }

    private static boolean isTableHeader(String[] lines, int idx) {
        if (idx + 1 >= lines.length) return false;
        String header = lines[idx].trim();
        String sep = lines[idx + 1].trim();
        if (!header.contains("|") || !sep.contains("-")) return false;
        return sep.matches("^\\|?\\s*:?-{2,}:?\\s*(?:\\|\\s*:?-{2,}:?\\s*)*\\|?$");
    }

    private static String[] splitTableRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        String[] raw = s.split("\\|", -1);
        for (int i = 0; i < raw.length; i++) {
            raw[i] = raw[i].trim();
        }
        return raw;
    }

    private static String[] parseTableAlignments(String sepLine) {
        String[] parts = splitTableRow(sepLine);
        String[] aligns = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            boolean left = p.startsWith(":");
            boolean right = p.endsWith(":");
            if (left && right) aligns[i] = "center";
            else if (right) aligns[i] = "right";
            else aligns[i] = "start";
        }
        return aligns;
    }

    private static void appendTable(StringBuilder body, String[] headers, String[] aligns, List<String[]> rows) {
        body.append("<div class='table-wrap'><table><thead><tr>");
        for (int i = 0; i < headers.length; i++) {
            String align = i < aligns.length ? aligns[i] : "start";
            body.append("<th style='text-align:").append(align).append("'>").append(inlineMd(headers[i])).append("</th>");
        }
        body.append("</tr></thead><tbody>");
        for (String[] row : rows) {
            body.append("<tr>");
            int cols = Math.max(headers.length, row.length);
            for (int i = 0; i < cols; i++) {
                String align = i < aligns.length ? aligns[i] : "start";
                String val = i < row.length ? row[i] : "";
                body.append("<td style='text-align:").append(align).append("'>").append(inlineMd(val)).append("</td>");
            }
            body.append("</tr>");
        }
        body.append("</tbody></table></div>");
    }

    private static void appendBlockquoteOrAlert(StringBuilder body, List<String> quoteLines) {
        if (quoteLines.isEmpty()) return;
        Matcher alertM = GH_ALERT_HEADER_PATTERN.matcher(quoteLines.get(0).trim());
        if (alertM.matches()) {
            String type = alertM.group(1).toUpperCase(Locale.ROOT);
            String remainder = alertM.group(2) != null ? alertM.group(2).trim() : "";
            String cls;
            String label;
            String glyph;
            switch (type) {
                case "TIP":
                    cls = "gh-tip";
                    label = "טיפ • TIP";
                    glyph = "💡";
                    break;
                case "IMPORTANT":
                    cls = "gh-important";
                    label = "חשוב • IMPORTANT";
                    glyph = "◆";
                    break;
                case "WARNING":
                    cls = "gh-warning";
                    label = "אזהרה • WARNING";
                    glyph = "⚠";
                    break;
                case "CAUTION":
                    cls = "gh-caution";
                    label = "זהירות • CAUTION";
                    glyph = "⛔";
                    break;
                default:
                    cls = "gh-note";
                    label = "הערה • NOTE";
                    glyph = "ℹ";
                    break;
            }
            body.append("<div class='gh-alert ").append(cls).append("'>")
                    .append("<div class='gh-alert-title'><span>").append(glyph).append("</span><span>").append(label).append("</span></div>")
                    .append("<div class='gh-alert-body'>");
            if (!remainder.isEmpty()) {
                body.append("<p>").append(inlineMd(remainder)).append("</p>");
            }
            for (int i = 1; i < quoteLines.size(); i++) {
                String ql = quoteLines.get(i).trim();
                if (!ql.isEmpty()) {
                    body.append("<p>").append(inlineMd(ql)).append("</p>");
                }
            }
            body.append("</div></div>");
            return;
        }
        body.append("<blockquote>");
        for (String ql : quoteLines) {
            if (ql.trim().isEmpty()) {
                body.append("<br/>");
            } else {
                body.append("<p>").append(inlineMd(ql)).append("</p>");
            }
        }
        body.append("</blockquote>");
    }

    private static void appendCodeBubble(StringBuilder body, int blockIndex, String langHint, String codeStr, boolean darkBg) {
        String displayLang = (langHint == null || langHint.isEmpty()) ? "CODE" : escapeHtml(langHint);
        String highlighted = highlightCodeBlockToHtml(codeStr, langHint);
        body.append("<div class='code-bubble'>")
                .append("<div class='code-head'>")
                .append("<span class='code-lang'>").append(displayLang).append("</span>")
                .append("<button type='button' class='copy-btn' onclick='copyCodeBlock(").append(blockIndex).append(",this)'>העתק</button>")
                .append("</div>")
                .append("<pre class='code-pre'><code>").append(highlighted).append("</code></pre>")
                .append("</div>");
    }

    private static String highlightCodeBlockToHtml(String rawCode, String langHint) {
        if (rawCode == null || rawCode.isEmpty()) return "";
        String lang = langHint == null ? "" : langHint.trim().toLowerCase(Locale.ROOT);
        boolean isXmlOrHtml = lang.equals("xml") || lang.equals("html") || lang.equals("htm") || lang.equals("svg");
        boolean isHashComment = lang.equals("py") || lang.equals("python") || lang.equals("sh")
                || lang.equals("bash") || lang.equals("zsh") || lang.equals("yaml") || lang.equals("yml")
                || lang.equals("properties") || lang.equals("ini") || lang.equals("toml");
        boolean isSql = lang.equals("sql");

        StringBuilder out = new StringBuilder(rawCode.length() + 64);
        int i = 0;
        int n = rawCode.length();
        while (i < n) {
            char c = rawCode.charAt(i);

            // C-style line comment //
            if (!isXmlOrHtml && c == '/' && i + 1 < n && rawCode.charAt(i + 1) == '/') {
                int end = rawCode.indexOf('\n', i);
                if (end < 0) end = n;
                out.append("<span class='tok-com'>").append(escapeHtml(rawCode.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            // C-style block comment /* ... */
            if (!isXmlOrHtml && c == '/' && i + 1 < n && rawCode.charAt(i + 1) == '*') {
                int end = rawCode.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                out.append("<span class='tok-com'>").append(escapeHtml(rawCode.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            // SQL line comment --
            if (isSql && c == '-' && i + 1 < n && rawCode.charAt(i + 1) == '-') {
                int end = rawCode.indexOf('\n', i);
                if (end < 0) end = n;
                out.append("<span class='tok-com'>").append(escapeHtml(rawCode.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            // Hash comment #
            if (isHashComment && c == '#') {
                int end = rawCode.indexOf('\n', i);
                if (end < 0) end = n;
                out.append("<span class='tok-com'>").append(escapeHtml(rawCode.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            // XML comment <!-- ... -->
            if (isXmlOrHtml && c == '<' && rawCode.startsWith("<!--", i)) {
                int end = rawCode.indexOf("-->", i + 4);
                end = end < 0 ? n : end + 3;
                out.append("<span class='tok-com'>").append(escapeHtml(rawCode.substring(i, end))).append("</span>");
                i = end;
                continue;
            }
            // XML/HTML tag name
            if (isXmlOrHtml && c == '<') {
                int j = i + 1;
                if (j < n && rawCode.charAt(j) == '/') j++;
                int nameStart = j;
                while (j < n && (Character.isLetterOrDigit(rawCode.charAt(j)) || rawCode.charAt(j) == ':' || rawCode.charAt(j) == '-' || rawCode.charAt(j) == '_')) {
                    j++;
                }
                if (j > nameStart) {
                    out.append(escapeHtml(rawCode.substring(i, nameStart)))
                            .append("<span class='tok-tag'>").append(escapeHtml(rawCode.substring(nameStart, j))).append("</span>");
                    i = j;
                    continue;
                }
            }
            // Quoted strings (", ', `)
            if (c == '"' || c == '\'' || c == '`') {
                char quote = c;
                int j = i + 1;
                while (j < n) {
                    char qc = rawCode.charAt(j);
                    if (qc == '\\' && j + 1 < n) {
                        j += 2;
                        continue;
                    }
                    if (qc == quote || (qc == '\n' && quote != '`')) {
                        if (qc == quote) j++;
                        break;
                    }
                    j++;
                }
                out.append("<span class='tok-str'>").append(escapeHtml(rawCode.substring(i, j))).append("</span>");
                i = j;
                continue;
            }
            // Annotations @Foo
            if (c == '@' && i + 1 < n && Character.isJavaIdentifierStart(rawCode.charAt(i + 1))) {
                int j = i + 2;
                while (j < n && Character.isJavaIdentifierPart(rawCode.charAt(j))) j++;
                out.append("<span class='tok-anno'>").append(escapeHtml(rawCode.substring(i, j))).append("</span>");
                i = j;
                continue;
            }
            // Numbers
            if (Character.isDigit(c) && (i == 0 || !Character.isJavaIdentifierPart(rawCode.charAt(i - 1)))) {
                int j = i + 1;
                while (j < n) {
                    char nc = rawCode.charAt(j);
                    if (Character.isLetterOrDigit(nc) || nc == '.' || nc == '_') j++;
                    else break;
                }
                out.append("<span class='tok-num'>").append(escapeHtml(rawCode.substring(i, j))).append("</span>");
                i = j;
                continue;
            }
            // Identifiers & keywords
            if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(rawCode.charAt(j))) j++;
                String word = rawCode.substring(i, j);
                if (KEYWORDS_COMMON.contains(word) || KEYWORDS_COMMON.contains(word.toUpperCase(Locale.ROOT))) {
                    out.append("<span class='tok-kw'>").append(escapeHtml(word)).append("</span>");
                } else {
                    out.append(escapeHtml(word));
                }
                i = j;
                continue;
            }
            out.append(escapeHtmlChar(c));
            i++;
        }
        return out.toString();
    }

    private static String inlineMd(String s) {
        if (s == null || s.isEmpty()) return "";
        // Protect inline code spans first so markdown delimiters inside backticks stay literal
        List<String> codeSnippets = new ArrayList<>();
        StringBuilder protectedCode = new StringBuilder();
        int idx = 0;
        while (idx < s.length()) {
            int tick = s.indexOf('`', idx);
            if (tick < 0) {
                protectedCode.append(s.substring(idx));
                break;
            }
            int endTick = s.indexOf('`', tick + 1);
            if (endTick < 0) {
                protectedCode.append(s.substring(idx));
                break;
            }
            protectedCode.append(s, idx, tick);
            String placeholder = "\uE000INLCODE" + codeSnippets.size() + "\uE001";
            codeSnippets.add("<code>" + escapeHtml(s.substring(tick + 1, endTick)) + "</code>");
            protectedCode.append(placeholder);
            idx = endTick + 1;
        }

        String esc = escapeHtml(protectedCode.toString());

        // Safe inline images ![alt](url)
        Matcher imgM = INLINE_IMAGE_PATTERN.matcher(esc);
        StringBuffer imgSb = new StringBuffer();
        while (imgM.find()) {
            String alt = imgM.group(1);
            String url = unescapeBasic(imgM.group(2).trim());
            if (isSafeExternalUrl(url)) {
                imgM.appendReplacement(imgSb, Matcher.quoteReplacement("<img src='" + escapeAttr(url) + "' alt='" + escapeAttr(unescapeBasic(alt)) + "'/>"));
            } else {
                imgM.appendReplacement(imgSb, Matcher.quoteReplacement("<span class='badge-chip'>🖼 " + alt + "</span>"));
            }
        }
        imgM.appendTail(imgSb);
        esc = imgSb.toString();

        // Safe links [text](url) — strictly http://, https://, or # anchors
        Matcher linkM = INLINE_LINK_PATTERN.matcher(esc);
        StringBuffer linkSb = new StringBuffer();
        while (linkM.find()) {
            String label = linkM.group(1);
            String url = unescapeBasic(linkM.group(2).trim());
            if (isSafeHref(url)) {
                linkM.appendReplacement(linkSb, Matcher.quoteReplacement("<a href='" + escapeAttr(url) + "'>" + label + "</a>"));
            } else {
                linkM.appendReplacement(linkSb, Matcher.quoteReplacement(label));
            }
        }
        linkM.appendTail(linkSb);
        esc = linkSb.toString();

        // Strikethrough, Bold+Italic, Bold, Underline, Italic
        esc = esc.replaceAll("~~([^~]+)~~", "<del>$1</del>");
        esc = esc.replaceAll("\\*\\*\\*([^*]+)\\*\\*\\*", "<b><i>$1</i></b>");
        esc = esc.replaceAll("___([^_]+)___", "<b><i>$1</i></b>");
        esc = esc.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        esc = esc.replaceAll("__([^_]+)__", "<u>$1</u>");
        esc = esc.replaceAll("\\*([^*\\n]+)\\*", "<i>$1</i>");
        esc = esc.replaceAll("(?<![A-Za-z0-9])_([^_\\n]+)_(?![A-Za-z0-9])", "<i>$1</i>");

        for (int i = 0; i < codeSnippets.size(); i++) {
            esc = esc.replace("\uE000INLCODE" + i + "\uE001", codeSnippets.get(i));
        }
        return esc;
    }

    private static boolean isSafeExternalUrl(String url) {
        if (url == null) return false;
        String low = url.trim().toLowerCase(Locale.ROOT);
        return low.startsWith("https://") || low.startsWith("http://");
    }

    private static boolean isSafeHref(String url) {
        if (url == null) return false;
        String t = url.trim();
        if (t.startsWith("#") && t.length() > 1) return true;
        String low = t.toLowerCase(Locale.ROOT);
        return low.startsWith("https://") || low.startsWith("http://");
    }

    private static String sanitizeCssColor(String color, String fallback) {
        if (color == null) return fallback;
        String t = color.trim();
        if (t.matches("^#[0-9a-fA-F]{6}$") || t.matches("^#[0-9a-fA-F]{8}$")) {
            return t;
        }
        return fallback;
    }

    private static String unescapeBasic(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }

    private static String escapeHtml(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            sb.append(escapeHtmlChar(s.charAt(i)));
        }
        return sb.toString();
    }

    private static String escapeHtmlChar(char c) {
        switch (c) {
            case '&': return "&amp;";
            case '<': return "&lt;";
            case '>': return "&gt;";
            case '"': return "&quot;";
            case '\'': return "&#39;";
            default: return String.valueOf(c);
        }
    }
}
