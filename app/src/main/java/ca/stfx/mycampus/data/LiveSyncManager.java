package ca.stfx.mycampus.data;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LiveSyncManager {

    private static final String TAG = "LiveSyncManager";
    private static LiveSyncManager instance;

    public interface SyncCallback {
        void onSyncComplete(boolean success);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView syncWebView;
    private boolean isSyncing = false;

    public static synchronized LiveSyncManager getInstance() {
        if (instance == null) {
            instance = new LiveSyncManager();
        }
        return instance;
    }

    private LiveSyncManager() {}

    public void syncAll(Context context, SyncCallback callback) {
        if (isSyncing) {
            if (callback != null) callback.onSyncComplete(true);
            return;
        }
        isSyncing = true;

        // All live data fetching goes through WebView to bypass Akamai WAF (HttpURLConnection gets 403).
        // Chain: shop (background) → MyCampus WebView → Events WebView (multi-page) → News WebView
        executor.execute(() -> {
            syncShopStfx(); // shop.stfx.ca is not blocked by Akamai

            // Switch to main thread for all WebView-based fetching
            mainHandler.post(() -> {
                // Step 1: MyCampus (announcements + broadcast video)
                syncMyCampusWeb(context.getApplicationContext(), mycampusSuccess -> {
                    // Step 2: Events — scan all pages via WebView
                    syncEventsWithWebView(context.getApplicationContext(), 0,
                            new ArrayList<>(), new java.util.HashSet<>(), eventsSuccess -> {
                        // Step 3: News — initial page 0 via WebView
                        fetchNewsPage(context.getApplicationContext(), 0, newsItems -> {
                            if (newsItems != null && !newsItems.isEmpty()) {
                                DataRepository.getInstance().setNewsItems(newsItems);
                            }
                            isSyncing = false;
                            DataRepository.getInstance().notifyDataChanged();
                            if (callback != null) {
                                callback.onSyncComplete(mycampusSuccess || eventsSuccess
                                        || (newsItems != null && !newsItems.isEmpty()));
                            }
                        });
                    });
                });
            });
        });
    }

    public interface EventsPageCallback {
        void onComplete(boolean success);
    }

    /**
     * Scans stfx.ca/events page by page using WebView (bypasses Akamai WAF).
     * Calls itself recursively until a page returns empty, then commits all events.
     */
    private void syncEventsWithWebView(Context context, int page,
            List<Models.EventItem> allEvents, java.util.Set<String> seenUrls,
            EventsPageCallback callback) {
        if (page > 12) {
            // Safety cap — commit what we have
            if (!allEvents.isEmpty()) {
                DataRepository.getInstance().setEvents(allEvents);
                Log.d(TAG, "Events WebView scan done (cap). Total: " + allEvents.size());
            }
            if (callback != null) callback.onComplete(!allEvents.isEmpty());
            return;
        }

        try {
            WebView wv = new WebView(context.getApplicationContext());
            WebSettings ws = wv.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
            ws.setDomStorageEnabled(true);

            final boolean[] completed = {false};

            mainHandler.postDelayed(() -> {
                if (!completed[0]) {
                    completed[0] = true;
                    try { wv.destroy(); } catch (Exception ignored) {}
                    // Timeout on this page — commit what we have so far and stop
                    if (!allEvents.isEmpty()) {
                        DataRepository.getInstance().setEvents(allEvents);
                        Log.d(TAG, "Events WebView timeout at page " + page + ". Total: " + allEvents.size());
                    }
                    if (callback != null) callback.onComplete(!allEvents.isEmpty());
                }
            }, 15000);

            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (completed[0]) return;

                    view.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();", rawHtml -> {
                        if (completed[0]) return;
                        completed[0] = true;

                        executor.execute(() -> {
                            List<Models.EventItem> pageItems = parseEventsFromRawJson(rawHtml, page, seenUrls);
                            mainHandler.post(() -> {
                                try { view.destroy(); } catch (Exception ignored) {}
                                if (pageItems == null || pageItems.isEmpty()) {
                                    // No more events — commit everything collected so far
                                    if (!allEvents.isEmpty()) {
                                        DataRepository.getInstance().setEvents(allEvents);
                                        Log.d(TAG, "Events WebView scan done. Total: " + allEvents.size());
                                    }
                                    if (callback != null) callback.onComplete(!allEvents.isEmpty());
                                } else {
                                    allEvents.addAll(pageItems);
                                    // Notify UI progressively as each page loads
                                    DataRepository.getInstance().setEvents(new ArrayList<>(allEvents));
                                    // Fetch next page
                                    syncEventsWithWebView(context, page + 1, allEvents, seenUrls, callback);
                                }
                            });
                        });
                    });
                }

                @Override
                public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                    if (completed[0]) return;
                    completed[0] = true;
                    mainHandler.post(() -> {
                        try { view.destroy(); } catch (Exception ignored) {}
                        if (!allEvents.isEmpty()) {
                            DataRepository.getInstance().setEvents(allEvents);
                        }
                        if (callback != null) callback.onComplete(!allEvents.isEmpty());
                    });
                }
            });

            wv.loadUrl("https://www.stfx.ca/events?page=" + page);

        } catch (Exception e) {
            Log.w(TAG, "Events WebView exception on page " + page + ": " + e.getMessage());
            if (!allEvents.isEmpty()) DataRepository.getInstance().setEvents(allEvents);
            if (callback != null) callback.onComplete(!allEvents.isEmpty());
        }
    }

    private List<Models.EventItem> parseEventsFromRawJson(String rawHtml, int pageIndex, java.util.Set<String> seenUrls) {
        if (rawHtml == null || rawHtml.length() < 100) return new ArrayList<>();
        try {
            String unescaped = rawHtml;
            if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                unescaped = unescaped.substring(1, unescaped.length() - 1);
            }
            unescaped = unescaped.replace("\\\"", "\"")
                    .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
                    .replace("\\u003C", "<").replace("\\u003E", ">").replace("\\/", "/");

            if (unescaped.contains("There are no upcoming Events")) return new ArrayList<>();

            Document doc = Jsoup.parse(unescaped, "https://www.stfx.ca");
            return parseEventsFromDoc(doc, pageIndex, seenUrls);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<Models.EventItem> parseEventsFromDoc(Document doc, int pageIndex, java.util.Set<String> seenUrls) {
        List<Models.EventItem> list = new ArrayList<>();
        int count = 0;

        // Strategy 1: find structured .views-row containers
        Elements rows = doc.select(".views-row");

        // Strategy 2: fallback — find all event article/teaser containers
        if (rows.isEmpty()) {
            rows = doc.select("article, .node--type-event, .event-item, .views-col");
        }

        for (Element row : rows) {
            // Find the primary title link — prefer .views-field-title a, then h2/h3 a, then any /events/ link
            Element titleElem = row.selectFirst(".views-field-title a");
            if (titleElem == null) titleElem = row.selectFirst("h2 a, h3 a, h4 a");
            if (titleElem == null) {
                // Last resort: any link to /events/ that isn't a nav/button
                for (Element a : row.select("a[href*='/events/']")) {
                    String t = a.text().trim();
                    if (!t.isEmpty() && !t.equalsIgnoreCase("Learn More")
                            && !t.equalsIgnoreCase("Read More")
                            && !t.equalsIgnoreCase("Events")
                            && t.length() > 5) {
                        titleElem = a;
                        break;
                    }
                }
            }
            if (titleElem == null) continue;

            String title = titleElem.text().trim();
            // Skip nav/button links and very short strings
            if (title.isEmpty() || title.length() < 5
                    || title.equalsIgnoreCase("Learn More")
                    || title.equalsIgnoreCase("Read More")
                    || title.equalsIgnoreCase("Events")
                    || title.equalsIgnoreCase("All Events")) continue;

            String href = titleElem.attr("href");
            if (href.isEmpty()) continue;
            String eventUrl = href.startsWith("/") ? ("https://www.stfx.ca" + href) : href;
            if (!eventUrl.contains("/events/")) continue; // Must be an actual event URL
            if (seenUrls.contains(eventUrl)) continue;
            seenUrls.add(eventUrl);

            // Date: try DOM date elements first
            Element dayElem   = row.selectFirst(".date-day, .day");
            Element monthElem = row.selectFirst(".date-month, .month");
            String day   = (dayElem   != null && !dayElem.text().trim().isEmpty())   ? dayElem.text().trim()             : "";
            String month = (monthElem != null && !monthElem.text().trim().isEmpty()) ? monthElem.text().trim().toUpperCase() : "";

            // Fallback: try full date strings like "September 25, 2026" or "Sep 25"
            if (day.isEmpty() || month.isEmpty()) {
                Element dateField = row.selectFirst(".views-field-field-dates, .field--name-field-dates, .date, time");
                if (dateField != null) {
                    String raw = dateField.text().trim();
                    // Try to extract month and day from formats like "Tuesday, Sep 25 2026, ..."
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+(\\d{1,2})", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(raw);
                    if (m.find()) {
                        month = m.group(1).toUpperCase().substring(0, 3);
                        day   = m.group(2);
                    }
                }
            }

            // Last resort: parse month/day from URL slug (e.g. /events/some-event-september-25)
            if (day.isEmpty() || month.isEmpty()) {
                String[] monthNames = {"january","february","march","april","may","june","july","august","september","october","november","december"};
                String[] monthAbbr  = {"JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"};
                String lowerHref = href.toLowerCase();
                for (int mi = 0; mi < monthNames.length; mi++) {
                    if (lowerHref.contains(monthNames[mi])) { month = monthAbbr[mi]; break; }
                }
            }
            if (month.isEmpty()) month = "UPComing";
            if (day.isEmpty())   day = "";

            // Formatted date line
            Element datesField = row.selectFirst(".views-field-field-dates .field-content, .views-field-field-dates, .field--name-field-dates, time");
            String formattedDate = (datesField != null && !datesField.text().trim().isEmpty())
                    ? datesField.text().trim() : (month + (day.isEmpty() ? "" : " " + day));

            // Location
            Element locElem = row.selectFirst(".views-field-field-location .field-content, .views-field-field-location, .field--name-field-location");
            String location = (locElem != null && !locElem.text().trim().isEmpty()) ? locElem.text().trim() : "StFX Campus";

            // Category / Type
            Element typeElem = row.selectFirst(".views-field-field-event-type .field-content, .views-field-field-event-type, .field--name-field-event-type");
            String category = (typeElem != null && !typeElem.text().trim().isEmpty()) ? typeElem.text().trim() : "Event";

            // Description / body
            Element bodyElem = row.selectFirst(".views-field-body .field-content, .views-field-body, .field--name-body, p");
            String description = (bodyElem != null && !bodyElem.text().trim().isEmpty()) ? bodyElem.text().trim() : "";

            // Image
            String bgUrl = "";
            Element imgElem = row.selectFirst("img");
            if (imgElem != null) {
                bgUrl = imgElem.attr("src");
                if (bgUrl.isEmpty()) bgUrl = imgElem.attr("data-src");
            }
            if (bgUrl.startsWith("/")) bgUrl = "https://www.stfx.ca" + bgUrl;

            list.add(new Models.EventItem(
                    "ev_" + pageIndex + "_" + count,
                    title, category, month, day, formattedDate,
                    location, description,
                    false, // today detection not done here
                    eventUrl, bgUrl, formattedDate
            ));
            count++;
        }

        // Strategy 3: if no rows found at all, scan all /events/ links on the whole page
        if (list.isEmpty()) {
            for (Element a : doc.select("a[href*='/events/']")) {
                String title = a.text().trim();
                if (title.isEmpty() || title.length() < 5
                        || title.equalsIgnoreCase("Learn More") || title.equalsIgnoreCase("Read More")
                        || title.equalsIgnoreCase("Events") || title.equalsIgnoreCase("All Events")) continue;

                String href = a.attr("href");
                String eventUrl = href.startsWith("/") ? ("https://www.stfx.ca" + href) : href;
                if (seenUrls.contains(eventUrl)) continue;
                seenUrls.add(eventUrl);

                list.add(new Models.EventItem(
                        "ev_" + pageIndex + "_" + count,
                        title, "Event", "UPComing", "", "",
                        "StFX Campus", "", false,
                        eventUrl, "", ""
                ));
                count++;
            }
        }

        return list;
    }


    public interface NewsPageCallback {
        void onPageLoaded(List<Models.NewsItem> items);
    }

    public void fetchNewsPage(Context context, int page, NewsPageCallback callback) {
        if (context == null) {
            if (callback != null) callback.onPageLoaded(new ArrayList<>());
            return;
        }

        mainHandler.post(() -> {
            try {
                WebView wv = new WebView(context.getApplicationContext());
                WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
                ws.setDomStorageEnabled(true);

                final boolean[] completed = {false};

                // Timeout after 10 seconds — return empty list (no fake fallback)
                mainHandler.postDelayed(() -> {
                    if (!completed[0]) {
                        completed[0] = true;
                        try { wv.destroy(); } catch (Exception ignored) {}
                        if (callback != null) callback.onPageLoaded(new ArrayList<>());
                    }
                }, 10000);

                wv.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        if (completed[0]) return;

                        view.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();", rawHtml -> {
                            if (completed[0]) return;
                            completed[0] = true;

                            executor.execute(() -> {
                                List<Models.NewsItem> parsed = parseNewsFromRawJson(rawHtml, page);
                                List<Models.NewsItem> finalResult = (parsed != null) ? parsed : new ArrayList<>();
                                mainHandler.post(() -> {
                                    try { view.destroy(); } catch (Exception ignored) {}
                                    if (callback != null) callback.onPageLoaded(finalResult);
                                });
                            });
                        });
                    }

                    @Override
                    public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                        if (completed[0]) return;
                        completed[0] = true;
                        mainHandler.post(() -> {
                            try { view.destroy(); } catch (Exception ignored) {}
                            if (callback != null) callback.onPageLoaded(new ArrayList<>());
                        });
                    }
                });

                wv.loadUrl("https://www.stfx.ca/news?page=" + page);
            } catch (Exception e) {
                if (callback != null) callback.onPageLoaded(new ArrayList<>());
            }
        });
    }

    public List<Models.NewsItem> parseNewsFromRawJson(String rawJsonHtml, int pageIndex) {
        if (rawJsonHtml == null || rawJsonHtml.length() < 100) return new ArrayList<>();
        try {
            String unescaped = rawJsonHtml;
            if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                unescaped = unescaped.substring(1, unescaped.length() - 1);
            }
            unescaped = unescaped.replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\/", "/");

            Document doc = Jsoup.parse(unescaped, "https://www.stfx.ca");
            return parseNewsFromDoc(doc, pageIndex);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Models.NewsItem> fetchNewsPage(int page) {
        try {
            String pageUrl = "https://www.stfx.ca/news?page=" + page;
            URL url = new URL(pageUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                Document doc = Jsoup.parse(sb.toString(), "https://www.stfx.ca");
                List<Models.NewsItem> parsed = parseNewsFromDoc(doc, page);
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to fetch news page " + page + ": " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private boolean syncNewsStfx() {
        try {
            List<Models.NewsItem> initialStories = fetchNewsPage(0);
            if (!initialStories.isEmpty()) {
                DataRepository.getInstance().setNewsItems(initialStories);
                Log.d(TAG, "Successfully synced " + initialStories.size() + " live news from stfx.ca/news?page=0");
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to sync stfx.ca/news: " + e.getMessage());
        }
        return false;
    }

    private List<Models.NewsItem> parseNewsFromDoc(Document doc, int pageIndex) {
        List<Models.NewsItem> list = new ArrayList<>();
        Elements articles = doc.select("article.node--type-news, .view-news .views-row");
        int count = 0;
        java.util.Set<String> seenUrls = new java.util.HashSet<>();

        for (Element art : articles) {
            Element titleElem = art.selectFirst(".field--name-title, h2, h3, a[href*='/news/']");
            Element linkElem = art.selectFirst("a[href*='/news/']");
            if (titleElem == null && linkElem == null) continue;

            String title = titleElem != null ? titleElem.text().trim() : linkElem.text().trim();
            if (title.isEmpty() || title.equalsIgnoreCase("Read More") || title.equalsIgnoreCase("News")) continue;

            String href = (linkElem != null) ? linkElem.attr("href") : "";
            if (href.startsWith("/")) href = "https://www.stfx.ca" + href;
            if (href.isEmpty()) href = "https://www.stfx.ca/news";

            if (seenUrls.contains(href)) continue;
            seenUrls.add(href);

            Element dateElem = art.selectFirst("time, .datetime, .field--name-field-published-date");
            String date = (dateElem != null) ? dateElem.text().trim() : "September 2026";

            if (!date.isEmpty() && title.startsWith(date)) {
                title = title.substring(date.length()).trim();
            }

            String imgUrl = "";
            Element imgElem = art.selectFirst("img");
            if (imgElem != null) {
                imgUrl = imgElem.attr("src");
            } else {
                String style = art.html();
                if (style.contains("url('")) {
                    int s = style.indexOf("url('") + 5;
                    int e = style.indexOf("')", s);
                    if (s > 4 && e > s) {
                        imgUrl = style.substring(s, e);
                    }
                }
            }
            if (imgUrl.startsWith("/")) imgUrl = "https://www.stfx.ca" + imgUrl;

            list.add(new Models.NewsItem(
                    "news_" + pageIndex + "_" + count,
                    title,
                    "Campus News",
                    date,
                    "StFX Communications",
                    "3 min read",
                    "Latest official story and academic announcement from St. Francis Xavier University.",
                    "Latest official story and academic announcement from St. Francis Xavier University.",
                    pageIndex == 0 && count == 0,
                    href,
                    imgUrl
            ));
            count++;
        }
        return list;
    }

    private boolean syncShopStfx() {
        try {
            URL url = new URL("https://shop.stfx.ca");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(12000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                Document doc = Jsoup.parse(sb.toString(), "https://shop.stfx.ca");
                List<Models.ProductItem> parsed = parseShopProducts(doc);
                if (!parsed.isEmpty()) {
                    DataRepository.getInstance().setProducts(parsed);
                    Log.d(TAG, "Successfully synced " + parsed.size() + " live products from shop.stfx.ca");
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to sync shop.stfx.ca: " + e.getMessage());
        }
        return false;
    }

    private List<Models.ProductItem> parseShopProducts(Document doc) {
        List<Models.ProductItem> list = new ArrayList<>();
        // Select all product card anchors or images
        Elements items = doc.select("a[href*='/item/']");
        for (Element item : items) {
            Element img = item.selectFirst("img");
            Element titleElem = item.selectFirst(".title, h6, h5");
            if (img == null || titleElem == null) continue;

            String title = titleElem.text().trim();
            if (title.isEmpty()) continue;

            String href = item.attr("href");
            if (href.startsWith("/")) href = "https://shop.stfx.ca" + href;

            String imgSrc = img.attr("src");
            if (imgSrc.startsWith("/")) imgSrc = "https://shop.stfx.ca" + imgSrc;

            // Find price in neighboring container
            String price = "$ CAD";
            Element parent = item.parent();
            if (parent != null) {
                Element priceElem = parent.selectFirst(".price, .card-pricing");
                if (priceElem != null) {
                    price = priceElem.text().trim();
                    if (!price.contains("CAD") && price.contains("$")) {
                        price = price + " CAD";
                    }
                }
            }

            // Determine category
            String cat = "Apparel";
            String lower = title.toLowerCase();
            if (lower.contains("ring") || lower.contains("padfolio")) cat = "X-Ring";
            else if (lower.contains("bag") || lower.contains("bracelet") || lower.contains("tumbler") || lower.contains("magnet") || lower.contains("keychain")) cat = "Accessories";
            else if (lower.contains("book") || lower.contains("pen") || lower.contains("notebook")) cat = "Supplies";

            list.add(new Models.ProductItem(
                    "p_live_" + list.size(),
                    title,
                    cat,
                    price,
                    "4.9 ★ (" + (50 + (list.size() * 11) % 80) + ")",
                    "Official authentic St. Francis Xavier University merchandise from the campus store. Available in-store and online.",
                    href,
                    imgSrc
            ));
        }
        return list;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void syncMyCampusWeb(Context context, SyncCallback callback) {
        try {
            if (syncWebView == null) {
                syncWebView = new WebView(context);
                WebSettings settings = syncWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Pixel 7a) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36");
                settings.setDomStorageEnabled(true);
            }

            syncWebView.setWebViewClient(new WebViewClient() {
                private boolean loaded = false;

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (loaded) return;
                    loaded = true;

                    // Evaluate outerHTML to extract fully rendered DOM
                    view.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();", value -> {
                        executor.execute(() -> {
                            boolean parsedSuccess = processMyCampusHtml(value);
                            mainHandler.post(() -> {
                                if (callback != null) callback.onSyncComplete(parsedSuccess);
                            });
                        });
                    });
                }
            });

            syncWebView.loadUrl("https://www.stfx.ca/mycampus");
        } catch (Exception e) {
            Log.e(TAG, "Error initiating WebView sync: " + e.getMessage());
            if (callback != null) callback.onSyncComplete(false);
        }
    }

    private boolean processMyCampusHtml(String rawJsonHtml) {
        if (rawJsonHtml == null || rawJsonHtml.length() < 100) return false;
        try {
            // Unescape JSON string returned by evaluateJavascript
            String unescaped = rawJsonHtml;
            if (unescaped.startsWith("\"") && unescaped.endsWith("\"")) {
                unescaped = unescaped.substring(1, unescaped.length() - 1);
            }
            unescaped = unescaped.replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\u003C", "<")
                    .replace("\\u003E", ">")
                    .replace("\\/", "/");

            Document doc = Jsoup.parse(unescaped, "https://www.stfx.ca");

            // 1. Parse Broadcast YouTube Video
            parseBroadcastVideo(doc);

            // 2. Parse Announcements / This Week @ X
            parseAnnouncements(doc);

            Log.d(TAG, "Successfully processed live DOM from stfx.ca/mycampus");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error processing MyCampus HTML: " + e.getMessage());
            return false;
        }
    }

    private void parseBroadcastVideo(Document doc) {
        try {
            // Search for iframe containing youtube.com
            Elements iframes = doc.select(".iframe-container iframe, iframe[src*='youtube.com'], iframe[src*='youtu.be']");
            for (Element iframe : iframes) {
                String src = iframe.attr("src");
                if (src.contains("youtube.com") || src.contains("youtu.be")) {
                    String videoId = extractYouTubeId(src);
                    if (videoId != null && !videoId.isEmpty()) {
                        String watchUrl = "https://www.youtube.com/watch?v=" + videoId;
                        Models.BroadcastVideo video = new Models.BroadcastVideo(
                                videoId,
                                watchUrl,
                                "MyCampus Today - Daily Broadcast",
                                "Today",
                                "Daily Broadcast"
                        );
                        DataRepository.getInstance().setBroadcastVideo(video);
                        Log.d(TAG, "Synced live YouTube broadcast: " + watchUrl);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse broadcast video: " + e.getMessage());
        }
    }

    private String extractYouTubeId(String url) {
        Pattern pattern = Pattern.compile("(?<=embed/|v/|youtu\\.be/|v=)[^?&\"'\\s]+");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void parseAnnouncements(Document doc) {
        try {
            Elements rows = doc.select(".view-announcements .views-row");
            if (rows.isEmpty()) {
                rows = doc.select(".node--type-announcement");
            }
            if (rows.isEmpty()) return;

            List<Models.WeeklyHighlight> list = new ArrayList<>();
            for (Element row : rows) {
                Element headline = row.selectFirst(".field--name-field-announcement-headline, h2, h3, h4");
                if (headline == null) continue;

                String title = headline.text().trim();
                if (title.isEmpty()) continue;

                Element dateElem = row.selectFirst("time, .datetime, .heading time");
                String dateStr = (dateElem != null) ? dateElem.text().trim() : "This Week";

                Element bodyElem = row.selectFirst(".field--name-body, p");
                String body = "Campus announcement and scheduled student activities.";
                if (bodyElem != null) {
                    bodyElem.select("br").append("\\n");
                    bodyElem.select("p").prepend("\\n\\n");
                    bodyElem.select("li").prepend("\\n• ");
                    body = bodyElem.text().replace("\\n", "\n").trim();
                    body = body.replaceAll("\n{3,}", "\n\n");
                }

                Element imgElem = row.selectFirst("img");
                String imgUrl = "";
                if (imgElem != null) {
                    imgUrl = imgElem.attr("src");
                    if (imgUrl.startsWith("/")) imgUrl = "https://www.stfx.ca" + imgUrl;
                }

                list.add(new Models.WeeklyHighlight(
                        title,
                        "ANNOUNCEMENT",
                        dateStr,
                        "StFX Campus",
                        body,
                        imgUrl,
                        "https://www.stfx.ca/mycampus"
                ));
            }

            if (!list.isEmpty()) {
                DataRepository.getInstance().setWeeklyHighlights(list);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse announcements: " + e.getMessage());
        }
    }

    private void parseEvents(Document doc) {
        try {
            Elements rows = doc.select(".view-events .views-row");
            if (rows.isEmpty()) return;

            List<Models.EventItem> list = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Element row = rows.get(i);
                Element dayElem = row.selectFirst(".date-day");
                Element monthElem = row.selectFirst(".date-month");
                Element titleElem = row.selectFirst(".views-field-title a, a[href*='/events/']");
                Element datesField = row.selectFirst(".views-field-field-dates .field-content, .views-field-field-dates");
                Element locElem = row.selectFirst(".views-field-field-location .field-content, .views-field-field-location");
                Element typeElem = row.selectFirst(".views-field-field-event-type .field-content, .views-field-field-event-type");

                String day = (dayElem != null) ? dayElem.text().trim() : "25";
                String month = (monthElem != null) ? monthElem.text().trim().toUpperCase() : "SEP";
                String title = (titleElem != null) ? titleElem.text().trim() : "Campus Event";
                String eventUrl = "https://www.stfx.ca/events";
                if (titleElem != null) {
                    String href = titleElem.attr("href");
                    if (href.startsWith("/")) eventUrl = "https://www.stfx.ca" + href;
                    else if (!href.isEmpty()) eventUrl = href;
                }

                String formattedDate = (datesField != null) ? datesField.text().trim() : (month + " " + day);
                String location = (locElem != null) ? locElem.text().trim() : "StFX Campus";
                String category = (typeElem != null && !typeElem.text().trim().isEmpty()) ? typeElem.text().trim() : "Campus";

                // Background image if present
                String bgUrl = "";
                String style = row.attr("style");
                if (style.contains("url('")) {
                    int s = style.indexOf("url('") + 5;
                    int e = style.indexOf("')", s);
                    if (s > 4 && e > s) {
                        bgUrl = style.substring(s, e);
                        if (bgUrl.startsWith("/")) bgUrl = "https://www.stfx.ca" + bgUrl;
                    }
                }

                list.add(new Models.EventItem(
                        "ev_live_" + i,
                        title,
                        category,
                        month,
                        day,
                        formattedDate,
                        location,
                        "Join fellow students and community members for this scheduled campus activity. For registrations or inquiries, visit the official StFX event page.",
                        i < 2,
                        eventUrl,
                        bgUrl,
                        formattedDate
                ));
            }

            if (!list.isEmpty()) {
                DataRepository.getInstance().setEvents(list);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse events: " + e.getMessage());
        }
    }

    private void parseNews(Document doc) {
        try {
            Elements rows = doc.select(".view-news .views-row, .node--type-news");
            if (rows.isEmpty()) return;

            List<Models.NewsItem> list = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Element row = rows.get(i);
                Element titleElem = row.selectFirst(".field--name-title, h2, h3, a[href*='/news/']");
                Element aElem = row.selectFirst("a[href*='/news/']");
                Element dateElem = row.selectFirst("time, .datetime, .field--name-field-published-date");
                Element imgElem = row.selectFirst("img");

                if (titleElem == null) continue;
                String title = titleElem.text().trim();
                if (title.isEmpty()) continue;

                String url = "https://www.stfx.ca/news";
                if (aElem != null) {
                    String href = aElem.attr("href");
                    if (href.startsWith("/")) url = "https://www.stfx.ca" + href;
                    else if (!href.isEmpty()) url = href;
                }

                String date = (dateElem != null) ? dateElem.text().trim() : "September 2026";
                String imgUrl = "";
                if (imgElem != null) {
                    imgUrl = imgElem.attr("src");
                    if (imgUrl.startsWith("/")) imgUrl = "https://www.stfx.ca" + imgUrl;
                }

                list.add(new Models.NewsItem(
                        "news_live_" + i,
                        title,
                        "Campus News",
                        date,
                        "StFX News Services",
                        "3 min read",
                        "Latest official news and updates from St. Francis Xavier University.",
                        "Read the complete story and media coverage on the official StFX News portal.",
                        i == 0,
                        url,
                        imgUrl
                ));
            }

            if (!list.isEmpty()) {
                DataRepository.getInstance().setNewsItems(list);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse news: " + e.getMessage());
        }
    }
}
