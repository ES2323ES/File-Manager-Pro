package com.aishhatorah.filemanagerpro;

import org.junit.Test;

import static org.junit.Assert.*;

public class CodeSyntaxHighlighterTest {

    @Test
    public void detectsAll20LanguagesAccurately() {
        assertEquals(CodeSyntaxHighlighter.Language.TYPESCRIPT, CodeSyntaxHighlighter.detectLanguage("App.tsx"));
        assertEquals(CodeSyntaxHighlighter.Language.JAVASCRIPT, CodeSyntaxHighlighter.detectLanguage("index.mjs"));
        assertEquals(CodeSyntaxHighlighter.Language.PYTHON, CodeSyntaxHighlighter.detectLanguage("script.py"));
        assertEquals(CodeSyntaxHighlighter.Language.SHELL, CodeSyntaxHighlighter.detectLanguage("deploy.sh"));
        assertEquals(CodeSyntaxHighlighter.Language.JAVA, CodeSyntaxHighlighter.detectLanguage("MainActivity.java"));
        assertEquals(CodeSyntaxHighlighter.Language.KOTLIN, CodeSyntaxHighlighter.detectLanguage("Helper.kt"));
        assertEquals(CodeSyntaxHighlighter.Language.C_CPP, CodeSyntaxHighlighter.detectLanguage("engine.cpp"));
        assertEquals(CodeSyntaxHighlighter.Language.HTML, CodeSyntaxHighlighter.detectLanguage("index.html"));
        assertEquals(CodeSyntaxHighlighter.Language.CSS, CodeSyntaxHighlighter.detectLanguage("styles.scss"));
        assertEquals(CodeSyntaxHighlighter.Language.RUST, CodeSyntaxHighlighter.detectLanguage("lib.rs"));
        assertEquals(CodeSyntaxHighlighter.Language.BAT, CodeSyntaxHighlighter.detectLanguage("build.bat"));
        assertEquals(CodeSyntaxHighlighter.Language.PROPERTIES, CodeSyntaxHighlighter.detectLanguage("gradle.properties"));
        assertEquals(CodeSyntaxHighlighter.Language.SMALI, CodeSyntaxHighlighter.detectLanguage("Class$1.smali"));
        assertEquals(CodeSyntaxHighlighter.Language.PHP, CodeSyntaxHighlighter.detectLanguage("api.php"));
        assertEquals(CodeSyntaxHighlighter.Language.YAML, CodeSyntaxHighlighter.detectLanguage("release.yml"));
        assertEquals(CodeSyntaxHighlighter.Language.JSON, CodeSyntaxHighlighter.detectLanguage("package.json"));
        assertEquals(CodeSyntaxHighlighter.Language.XML, CodeSyntaxHighlighter.detectLanguage("AndroidManifest.xml"));
        assertEquals(CodeSyntaxHighlighter.Language.SQL, CodeSyntaxHighlighter.detectLanguage("schema.sql"));
        assertEquals(CodeSyntaxHighlighter.Language.MARKDOWN, CodeSyntaxHighlighter.detectLanguage("README.md"));
    }

    @Test
    public void markdownToHtmlRendersHeadingsListsCodeAndEscapesHtml() {
        String md = "# כותרת ראשית\n- פריט ראשון **מודגש**\n```java\nint x = 1 < 2;\n```";
        String html = CodeSyntaxHighlighter.markdownToHtml(md, true, "#FFFFFF", "#3B82F6");
        assertTrue(html.contains("<h1>כותרת ראשית</h1>"));
        assertTrue(html.contains("<li>פריט ראשון <b>מודגש</b></li>"));
        assertTrue(html.contains("int x = 1 &lt; 2;"));
    }
}
