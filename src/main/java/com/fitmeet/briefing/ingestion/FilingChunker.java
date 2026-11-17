package com.fitmeet.briefing.ingestion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public final class FilingChunker {
    public static final String PARSER_VERSION = "jsoup-1";
    public static final String CHUNK_VERSION = "blocks-1";
    private FilingChunker() {}

    public static List<String> chunks(byte[] html) {
        var document = Jsoup.parse(new String(html, StandardCharsets.UTF_8));
        document.select("head, script, style, noscript, template, svg, [hidden], [aria-hidden=true]").remove();
        document.getElementsByTag("ix:hidden").remove();
        for (Element styled : document.select("[style]")) {
            String style = styled.attr("style").toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", "");
            if (style.contains("display:none") || style.contains("visibility:hidden")) styled.remove();
        }
        if (document.body() == null) throw new IllegalArgumentException("SEC filing body is missing");
        List<String> blocks = new ArrayList<>();
        for (Element element : document.body().select("h1, h2, h3, p, li, tr, div")) {
            if (!element.tagName().equals("tr") && element.closest("table") != null) continue;
            if (element.tagName().equals("div") && element.children().stream().anyMatch(child ->
                    child.is("p, li, tr, div, h1, h2, h3") || !child.select("p, li, tr, div, h1, h2, h3").isEmpty())) continue;
            String text;
            if (element.tagName().equals("tr")) {
                text = String.join(" | ", element.select("th, td").eachText());
            } else {
                text = element.text();
            }
            text = text.replaceAll("\\s+", " ").trim();
            if (text.length() < 20 && !element.tagName().equals("tr")) continue;
            while (!text.isEmpty()) {
                int end = Math.min(text.length(), 1500);
                if (end < text.length()) {
                    int space = text.lastIndexOf(' ', end);
                    if (space > 750) end = space;
                }
                blocks.add(text.substring(0, end));
                text = text.substring(end).trim();
                if (blocks.size() > 1000) throw new IllegalArgumentException("SEC filing exceeds chunk limit");
            }
        }
        if (blocks.isEmpty()) throw new IllegalArgumentException("SEC filing contains no usable text");
        return List.copyOf(blocks);
    }
}
