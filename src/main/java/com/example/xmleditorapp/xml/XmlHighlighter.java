package com.example.xmleditorapp.xml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XmlHighlighter {

    // CSS styles for highlighting
    private static final String CSS_STYLE = """
        <style>
            body { font-family: monospace; background-color: #282c34; color: #abb2bf; padding: 10px; margin: 0; }
            .tag { color: #e06c75; } /* Red */
            .attribute-name { color: #d19a66; } /* Orange */
            .attribute-value { color: #98c379; } /* Green */
            .comment { color: #5c6370; } /* Gray */
            .text-content { color: #abb2bf; } /* Light Gray/White */
            .doctype { color: #c678dd; } /* Violet */
        </style>
        """;

    // Combined regex pattern to match all XML components
    private static final Pattern XML_PATTERN = Pattern.compile(
            // Group 1: Comment (<!--...-->)
            "(<!--[\\s\\S]*?-->)" +
                    // Group 2: Doctype/Processing Instruction (<?...?> or <!DOCTYPE ...>)
                    "|(<\\?.*\\?>|<!DOCTYPE[^>]*>)" +
                    // Group 3: Tag name (e.g., <book>)
                    "|(<[/]?[a-zA-Z0-9_:-]+)" +
                    // Group 4: Attribute name (e.g., id=)
                    "|([a-zA-Z_:-]+)(?=\\s*=)" +
                    // Group 5: Attribute value (e.g., "123")
                    "|(=[\"']?.*[\"'])" +
                    // Group 6: Closing tag characters (e.g., > or />)
                    "|([/]?\\s*>)" +
                    // Group 7: Regular text content (everything else)
                    "|([^<]+)",
            Pattern.DOTALL
    );

    /**
     * Converts raw XML text into HTML with inline CSS styling for syntax highlighting.
     * @param xmlSource The raw XML content.
     * @return The HTML content with syntax highlighting.
     */
    public static String highlight(String xmlSource) {
        if (xmlSource == null || xmlSource.isEmpty()) {
            return "<html>" + CSS_STYLE + "<body>No XML Source Available</body></html>";
        }

        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html>").append(CSS_STYLE).append("<body><pre>");

        Matcher matcher = XML_PATTERN.matcher(xmlSource);

        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();

            // Append any plain text content that was missed between matches (should be handled by Group 7, but for safety)
            if (start > lastEnd) {
                htmlBuilder.append(escapeHtml(xmlSource.substring(lastEnd, start)));
            }

            String match = matcher.group(0);
            String escapedMatch = escapeHtml(match);

            if (matcher.group(1) != null) {
                // Comment
                htmlBuilder.append("<span class=\"comment\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(2) != null) {
                // Doctype / Processing Instruction
                htmlBuilder.append("<span class=\"doctype\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(3) != null) {
                // Tag Name (start/end of tag: <name, </name)
                htmlBuilder.append("<span class=\"tag\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(4) != null) {
                // Attribute Name
                htmlBuilder.append("<span class=\"attribute-name\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(5) != null) {
                // Attribute Value
                htmlBuilder.append("<span class=\"attribute-value\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(6) != null) {
                // Tag Closing (>)
                htmlBuilder.append("<span class=\"tag\">").append(escapedMatch).append("</span>");
            } else if (matcher.group(7) != null) {
                // Text Content between tags
                htmlBuilder.append("<span class=\"text-content\">").append(escapedMatch).append("</span>");
            }

            lastEnd = matcher.end();
        }

        // Append any remaining text after the last match
        if (lastEnd < xmlSource.length()) {
            htmlBuilder.append(escapeHtml(xmlSource.substring(lastEnd)));
        }

        htmlBuilder.append("</pre></body></html>");
        return htmlBuilder.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}