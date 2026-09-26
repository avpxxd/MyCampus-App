package ca.stfx.mycampus.data;

public class Models {

    public static class EventItem {
        public String id;
        public String title;
        public String category; // Athletics, Academic, Social, Arts, Workshops
        public String dateMonth;
        public String dateDay;
        public String time;
        public String location;
        public String description;
        public boolean isToday;
        public String eventUrl;
        public String imageUrl;
        public String formattedDate;

        public EventItem(String id, String title, String category, String dateMonth, String dateDay,
                         String time, String location, String description, boolean isToday,
                         String eventUrl, String imageUrl, String formattedDate) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.dateMonth = dateMonth;
            this.dateDay = dateDay;
            this.time = time;
            this.location = location;
            this.description = description;
            this.isToday = isToday;
            this.eventUrl = eventUrl;
            this.imageUrl = imageUrl;
            this.formattedDate = (formattedDate != null && !formattedDate.isEmpty()) ? formattedDate : (dateMonth + " " + dateDay + " • " + time);
        }

        // Backward compatibility constructor
        public EventItem(String id, String title, String category, String dateMonth, String dateDay,
                         String time, String location, String description, boolean isToday) {
            this(id, title, category, dateMonth, dateDay, time, location, description, isToday,
                    "https://www.stfx.ca/events", "", dateMonth + " " + dateDay + " | " + time);
        }
    }

    public static class NewsItem {
        public String id;
        public String title;
        public String category; // Campus Life, Athletics, Research, Honours
        public String date;
        public String author;
        public String readTime;
        public String snippet;
        public String content;
        public boolean isFeatured;
        public String url;
        public String imageUrl;

        public NewsItem(String id, String title, String category, String date, String author,
                        String readTime, String snippet, String content, boolean isFeatured,
                        String url, String imageUrl) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.date = date;
            this.author = author;
            this.readTime = readTime;
            this.snippet = snippet;
            this.content = content;
            this.isFeatured = isFeatured;
            this.url = (url != null && !url.isEmpty()) ? url : "https://www.stfx.ca/news";
            this.imageUrl = (imageUrl != null) ? imageUrl : "";
        }

        // Backward compatibility constructor
        public NewsItem(String id, String title, String category, String date, String author,
                        String readTime, String snippet, String content, boolean isFeatured) {
            this(id, title, category, date, author, readTime, snippet, content, isFeatured,
                    "https://www.stfx.ca/news", "");
        }
    }

    public static class LinkItem {
        public String id;
        public String title;
        public String subtitle;
        public String url;
        public String category;
        public boolean isQuickLink;
        public String letter;

        public LinkItem(String id, String title, String subtitle, String url, String category, boolean isQuickLink) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.url = url;
            this.category = category;
            this.isQuickLink = isQuickLink;
            this.letter = (title != null && !title.isEmpty()) ? title.substring(0, 1).toUpperCase() : "#";
        }
    }

    public static class ProductItem {
        public String id;
        public String title;
        public String category; // Apparel, X-Ring, Accessories, Supplies
        public String price;
        public String rating;
        public String description;
        public String shopUrl;
        public String imageUrl;

        public ProductItem(String id, String title, String category, String price,
                           String rating, String description, String shopUrl, String imageUrl) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.price = price;
            this.rating = rating;
            this.description = description;
            this.shopUrl = (shopUrl != null && !shopUrl.isEmpty()) ? shopUrl : "https://shop.stfx.ca";
            this.imageUrl = (imageUrl != null) ? imageUrl : "";
        }

        // Backward compatibility constructor
        public ProductItem(String id, String title, String category, String price,
                           String rating, String description, String shopUrl) {
            this(id, title, category, price, rating, description, shopUrl, "");
        }
    }

    public static class WeeklyHighlight {
        public String title;
        public String category;
        public String dateRange;
        public String location;
        public String description;
        public String imageUrl;
        public String detailUrl;

        public WeeklyHighlight(String title, String category, String dateRange, String location, String description, String imageUrl, String detailUrl) {
            this.title = title;
            this.category = category;
            this.dateRange = dateRange;
            this.location = location;
            this.description = description;
            this.imageUrl = (imageUrl != null) ? imageUrl : "";
            this.detailUrl = (detailUrl != null && !detailUrl.isEmpty()) ? detailUrl : "https://www.stfx.ca/mycampus";
        }

        public WeeklyHighlight(String title, String category, String dateRange, String location, String description) {
            this(title, category, dateRange, location, description, "", "https://www.stfx.ca/mycampus");
        }
    }

    public static class BroadcastVideo {
        public String videoId;
        public String videoUrl;
        public String title;
        public String date;
        public String duration;

        public BroadcastVideo(String videoId, String videoUrl, String title, String date, String duration) {
            this.videoId = videoId;
            this.videoUrl = videoUrl;
            this.title = title;
            this.date = date;
            this.duration = duration;
        }
    }
}
