package com.chaihq.webapp.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Prepares editor HTML for display. Used from templates as ${@richText.render(html)}.
 *
 * Stored HTML comes from browsers, so it is never trusted: it is cleaned against an allowlist of
 * the markup the editor produces, which strips scripts, event handlers and javascript: links.
 * Uploads are stored as <action-text-attachment> tags (the Action Text format the editor was
 * built for); browsers don't render those, so they become images or download links first.
 */
@Component("richText")
public class RichTextRenderer {

    // The editor's colour highlights, e.g. style="color: var(--highlight-3)"
    private static final Pattern HIGHLIGHT_STYLE =
            Pattern.compile("^\\s*(background-)?color:\\s*var\\(--highlight(-bg)?-\\d\\);?\\s*$");

    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("s", "mark", "figure", "figcaption", "hr")
            .addAttributes(":all", "class")
            .addAttributes("span", "style")
            .addAttributes("mark", "style")
            .addAttributes("pre", "data-language")
            .addAttributes("li", "value")
            .addAttributes("td", "colspan", "rowspan")
            .addAttributes("th", "colspan", "rowspan")
            .preserveRelativeLinks(true);

    public String render(String html) {
        if (html == null) {
            return null;
        }

        Document document = Jsoup.parseBodyFragment(html);
        document.outputSettings().prettyPrint(false);
        for (Element attachment : document.select("action-text-attachment")) {
            attachment.replaceWith(toFigure(attachment));
        }

        // Base URI only lets jsoup accept relative links like /attachments/...; it's never output
        String clean = Jsoup.clean(document.body().html(), "https://chai.invalid", SAFELIST,
                new Document.OutputSettings().prettyPrint(false));

        Document cleaned = Jsoup.parseBodyFragment(clean);
        cleaned.outputSettings().prettyPrint(false);
        for (Element styled : cleaned.select("[style]")) {
            if (!HIGHLIGHT_STYLE.matcher(styled.attr("style")).matches()) {
                styled.removeAttr("style");
            }
        }
        return cleaned.body().html();
    }

    private static Element toFigure(Element attachment) {
        String url = attachment.attr("url");
        String contentType = attachment.attr("content-type");
        String fileName = attachment.attr("filename");
        String caption = attachment.hasAttr("caption") ? attachment.attr("caption") : "";

        Element figure = new Element("figure").addClass("attachment");
        if (!isSafeUrl(url)) {
            return figure;
        }
        if (contentType.startsWith("image/")) {
            figure.addClass("attachment--preview");
            Element img = figure.appendElement("img").attr("src", url).attr("alt", attachment.attr("alt"));
            if (!attachment.attr("width").isEmpty()) img.attr("width", attachment.attr("width"));
            if (!attachment.attr("height").isEmpty()) img.attr("height", attachment.attr("height"));
            if (!caption.isBlank()) {
                figure.appendElement("figcaption").addClass("attachment__caption").text(caption);
            }
        } else {
            figure.addClass("attachment--file");
            Element link = figure.appendElement("a").attr("href", url);
            link.appendElement("span").addClass("attachment__name").text(caption.isBlank() ? fileName : caption);
        }
        return figure;
    }

    // Only same-site paths and http(s) links — never javascript:, data: and the like
    private static boolean isSafeUrl(String url) {
        return (url.startsWith("/") && !url.startsWith("//")) || url.startsWith("https://") || url.startsWith("http://");
    }
}
