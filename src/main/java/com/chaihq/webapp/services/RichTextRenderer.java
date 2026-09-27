package com.chaihq.webapp.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Prepares editor HTML for display. The editor stores uploads as <action-text-attachment>
 * tags (the Action Text format it was built for); browsers don't render those, so turn
 * them into images or download links. Used from templates as ${@richText.render(html)}.
 */
@Component("richText")
public class RichTextRenderer {

    public String render(String html) {
        if (html == null || !html.contains("action-text-attachment")) {
            return html;
        }

        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);

        for (Element attachment : document.select("action-text-attachment")) {
            String url = attachment.attr("url");
            if (!isSafeUrl(url)) {
                attachment.remove();
                continue;
            }

            String contentType = attachment.attr("content-type");
            String fileName = attachment.attr("filename");
            String caption = attachment.hasAttr("caption") ? attachment.attr("caption") : "";

            Element figure = new Element("figure");
            if (contentType.startsWith("image/")) {
                figure.addClass("attachment").addClass("attachment--preview");
                Element img = figure.appendElement("img").attr("src", url).attr("alt", attachment.attr("alt"));
                if (!attachment.attr("width").isEmpty()) img.attr("width", attachment.attr("width"));
                if (!attachment.attr("height").isEmpty()) img.attr("height", attachment.attr("height"));
                if (!caption.isBlank()) {
                    figure.appendElement("figcaption").addClass("attachment__caption").text(caption);
                }
            } else {
                figure.addClass("attachment").addClass("attachment--file");
                Element link = figure.appendElement("a").attr("href", url);
                link.appendElement("span").addClass("attachment__name").text(caption.isBlank() ? fileName : caption);
            }
            attachment.replaceWith(figure);
        }

        return document.body().html();
    }

    // Only same-site paths and http(s) links — never javascript:, data: and the like
    private static boolean isSafeUrl(String url) {
        return (url.startsWith("/") && !url.startsWith("//")) || url.startsWith("https://") || url.startsWith("http://");
    }
}
