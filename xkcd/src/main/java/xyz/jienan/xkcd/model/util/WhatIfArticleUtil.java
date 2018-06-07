package xyz.jienan.xkcd.model.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;
import xyz.jienan.xkcd.model.WhatIfArticle;

public class WhatIfArticleUtil {

    private static final String BASE_URI = "https://what-if.xkcd.com/";

    public static List<WhatIfArticle> getArticlesFromArchive(ResponseBody responseBody) throws IOException, ParseException {
        Document doc = Jsoup.parse(responseBody.string());
        Elements divArchive = doc.select("div#archive-wrapper");
        List<WhatIfArticle> articles = new ArrayList<>();
        for (Element element : divArchive.first().children()) {
            WhatIfArticle article = new WhatIfArticle();

            Element a = element.selectFirst("a[href]");
            String[] href = a.attr("href").split("/");
            article.num = Long.parseLong(href[href.length - 1]);
            article.featureImg = a.child(0).absUrl("src");

            Element b = element.selectFirst("h1.archive-title");
            article.title = b.child(0).html();

            Element c = element.selectFirst("h2.archive-date");
            String archiveDate = c.html();
            SimpleDateFormat sdf = new SimpleDateFormat("MMMMM d, yyyy");
            article.date = sdf.parse(archiveDate).getTime();

            articles.add(article);
        }
        return articles;
    }

    public static Document getArticleFromHtml(ResponseBody responseBody) throws IOException {
        Document doc = Jsoup.parse(responseBody.string(), BASE_URI);
        Elements elements = doc.select("article.entry");
        elements.remove(elements.get(0).children().first());
        Elements imageElements = doc.select("img.illustration");
        for (Element element : imageElements) {
            element.attr("src", element.absUrl("src"));
        }
        Elements pElements = doc.select("p");
        for (Element element : pElements) {
            if (element.html().split("\\[").length > 1) {
                element.attr("class", "latex");
            }
        }
        doc.head().html("");
        doc.head().appendElement("link").attr("rel", "stylesheet").attr("type", "text/css").attr("href", "style.css");
        doc.head().appendElement("script").attr("src", "https://cdnjs.cloudflare.com/ajax/libs/mathjax/2.7.4/latest.js?config=TeX-MML-AM_CHTML").attr("async", "");
        doc.head().appendElement("script").attr("src", "LatexInterface.js");
        doc.body().html(elements.html());
        return doc;
    }
}
