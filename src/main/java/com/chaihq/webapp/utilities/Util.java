package com.chaihq.webapp.utilities;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Util {
    public long reduceNumber(long number) {

        return number % 10;
    }

    public String markdownToHtml(String markdown) {
        MutableDataSet options = new MutableDataSet();

        // uncomment to set optional extensions
        //options.set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create(), StrikethroughExtension.create()));

        // uncomment to convert soft-breaks to hard breaks
        //options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");

        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();

        // You can re-use parser and renderer instances
        Node document = parser.parse(markdown);
        String html = renderer.render(document);  // "<p>This is <em>Sparta</em></p>\n"
        System.out.println(html);
        return html;
    }

    // The rich text editor submits markup like "<p><br></p>" when empty; treat that as no content
    public static String blankRichTextToEmpty(String html) {
        if (html == null) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(html);
        boolean hasText = !document.body().text().isBlank();
        boolean hasMedia = !document.body().select("img, video, iframe, action-text-attachment, hr, table").isEmpty();
        return hasText || hasMedia ? html : "";
    }
}
