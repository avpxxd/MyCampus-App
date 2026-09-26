package ca.stfx.mycampus.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataRepository {

    public interface OnDataChangeListener {
        void onDataChanged();
    }

    private static DataRepository instance;

    public static synchronized DataRepository getInstance() {
        if (instance == null) {
            instance = new DataRepository();
        }
        return instance;
    }

    private final List<Models.WeeklyHighlight> weeklyHighlights = new ArrayList<>();
    private final List<Models.EventItem> events = new ArrayList<>();
    private final List<Models.NewsItem> newsItems = new ArrayList<>();
    private final List<Models.LinkItem> allLinks = new ArrayList<>();
    private final List<Models.ProductItem> products = new ArrayList<>();
    private Models.BroadcastVideo broadcastVideo;
    private final List<OnDataChangeListener> listeners = new ArrayList<>();

    private DataRepository() {
        // No hardcoded data — all content is fetched live from stfx.ca
        initLinks();
    }

    public synchronized void addListener(OnDataChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(OnDataChangeListener listener) {
        listeners.remove(listener);
    }

    public synchronized void notifyDataChanged() {
        for (OnDataChangeListener listener : new ArrayList<>(listeners)) {
            try {
                listener.onDataChanged();
            } catch (Exception ignored) {}
        }
    }

    private void initLinks() {
        // Quick Links (Verified official URLs directly from stfx.ca/mycampus)
        allLinks.add(new Models.LinkItem("ql_banner", "Banner", "Course registration, student profile, transcript, tuition payment", "https://www.stfx.ca/banner", "Academics", true));
        allLinks.add(new Models.LinkItem("ql_moodle", "Moodle (Kwe')", "Access courses, syllabi, lecture notes, submit assignments", "https://moodle.stfx.ca/", "Academics", true));
        allLinks.add(new Models.LinkItem("ql_m365", "Microsoft 365", "Student Outlook email, Teams, OneDrive, Word & PowerPoint", "https://portal.office.com/", "Productivity", true));
        allLinks.add(new Models.LinkItem("ql_library", "Library", "Angus L. Macdonald Library catalog, study rooms, databases", "https://www.stfx.ca/library", "Library", true));
        allLinks.add(new Models.LinkItem("ql_mydata", "MyData", "University reporting, institutional metrics, student records", "https://mydata.stfx.ca/reports/browse", "Administration", true));
        allLinks.add(new Models.LinkItem("ql_dining", "Dining Services", "Morrison Hall menu, meal plan balances, meal hours", "https://stfxcampusfood.sodexomyway.com/", "Campus Life", true));
        allLinks.add(new Models.LinkItem("ql_athletics", "Athletics (Go X Go)", "Varsity schedules, Saputo Centre, intramural leagues", "https://www.goxgo.ca/", "Athletics", true));
        allLinks.add(new Models.LinkItem("ql_safety", "Safety & Security", "Campus safety, 24/7 security dispatch, SafeWalk program", "https://www.stfx.ca/safety-security", "Safety", true));
        allLinks.add(new Models.LinkItem("ql_health", "Health & Counselling", "Medical clinic, mental health counselling, appointments", "https://www.stfx.ca/student-services/support-services/health-counselling", "Wellness", true));
        allLinks.add(new Models.LinkItem("ql_career", "Student Career Centre", "Job postings, resume workshops, career counseling", "https://www.stfx.ca/student-services/support-services/student-career-centre", "Careers", true));
        allLinks.add(new Models.LinkItem("ql_it", "IT Services", "WiFi setup (eduroam), password reset, software portal", "https://www.stfx.ca/it-services", "IT Support", true));

        // A-Z Links (40 Verified official URLs directly from stfx.ca/mycampus Drupal index)
        allLinks.add(new Models.LinkItem("az_advising", "Academic Advising", "Degree planning, course changes, academic consultations", "https://www.stfx.ca/student-services/academic-services/academic-advising", "Academics", false));
        allLinks.add(new Models.LinkItem("az_success", "Academic Success Centre", "Writing, tutoring, study strategies, academic coaching", "https://www.stfx.ca/student-services/academic-services/academic-success-centre", "Academics", false));
        allLinks.add(new Models.LinkItem("az_accessibility_plan", "Accessibility Plan", "University multi-year accessibility commitments and plans", "https://www.stfx.ca/about/university-governance/strategic-plans/accessibility-plan", "Administration", false));
        allLinks.add(new Models.LinkItem("az_accessible", "Accessible Learning", "Academic accommodations, exam support, assistive technology", "https://www.stfx.ca/student-services/academic-services/accessible-learning", "Support", false));
        allLinks.add(new Models.LinkItem("az_art", "Art Gallery", "Exhibitions, permanent university collection, cultural events", "https://www.stfx.ca/art-gallery", "Culture", false));
        allLinks.add(new Models.LinkItem("az_athletics", "Athletics", "StFX Athletics, teams, schedules, varsity news", "https://www.goxgo.ca/", "Athletics", false));
        allLinks.add(new Models.LinkItem("az_maps", "Campus Maps", "Interactive campus map, building directory, parking lots", "https://www.stfx.ca/campus-maps", "Campus", false));
        allLinks.add(new Models.LinkItem("az_chaplaincy", "Chaplaincy", "Multi-faith services, university chapel, spiritual care", "https://www.stfx.ca/student-services/support-services/chaplaincy", "Support", false));
        allLinks.add(new Models.LinkItem("az_childcare", "Child Care", "University child care centre services and registration", "https://www.stfx.ca/student-services/support-services/childcare-stfx", "Services", false));
        allLinks.add(new Models.LinkItem("az_coop", "Co-operative Education Program", "Work-integrated learning, internships, employer network", "https://www.stfx.ca/programs-courses/co-operative-education", "Academics", false));
        allLinks.add(new Models.LinkItem("az_conferences", "Conferences and Events", "Facility booking, catering, summer accommodations", "https://www.stfx.ca/conferences-events", "Services", false));
        allLinks.add(new Models.LinkItem("az_dean_arts", "Dean of Arts", "Faculty of Arts programs, departments, academic policies", "https://www.stfx.ca/programs-courses/arts/dean-arts", "Faculties", false));
        allLinks.add(new Models.LinkItem("az_dean_biz", "Dean of Business", "Schwartz School of Business faculty and curriculum", "https://www.stfx.ca/programs-courses/business/dean-business", "Faculties", false));
        allLinks.add(new Models.LinkItem("az_dean_edu", "Dean of Education", "Faculty of Education programs and teacher certification", "https://www.stfx.ca/programs-courses/education/dean-education", "Faculties", false));
        allLinks.add(new Models.LinkItem("az_dean_sci", "Dean of Science", "Faculty of Science research, departments, and labs", "https://www.stfx.ca/programs-courses/science/dean-science", "Faculties", false));
        allLinks.add(new Models.LinkItem("az_dining", "Dining Services", "Morrison Hall, Bloomfield food court, cafe hours", "https://stfxcampusfood.sodexomyway.com/", "Campus Life", false));
        allLinks.add(new Models.LinkItem("az_facilities", "Facilities Management", "Campus maintenance, repairs, keys, and room bookings", "https://www.stfx.ca/facilities-management", "Services", false));
        allLinks.add(new Models.LinkItem("az_financial_aid", "Financial Aid Office", "Scholarships, bursaries, student loans, emergency aid", "https://www.stfx.ca/applications-admissions/financial-support/financial-aid-office", "Financial", false));
        allLinks.add(new Models.LinkItem("az_grad", "Graduate Studies", "Master's and PhD programs, thesis guidelines, funding", "https://www.stfx.ca/programs-courses/graduate-studies", "Academics", false));
        allLinks.add(new Models.LinkItem("az_health", "Health and Counselling Centre", "Physicians, nurse practitioners, mental health therapists", "https://www.stfx.ca/student-services/support-services/health-counselling", "Wellness", false));
        allLinks.add(new Models.LinkItem("az_equity", "Human Rights and Equity", "Anti-racism, discrimination policies, diversity initiatives", "https://www.stfx.ca/student-services/support-services/human-rights-equity", "Support", false));
        allLinks.add(new Models.LinkItem("az_it", "IT Services", "Technology helpdesk, wireless setup, classroom tech", "https://www.stfx.ca/it-services", "IT", false));
        allLinks.add(new Models.LinkItem("az_international", "Internationalization", "International student advising, exchange programs, visas", "https://www.stfx.ca/student-services/international", "Support", false));
        allLinks.add(new Models.LinkItem("az_moodle", "Kwe' (Moodle)", "StFX primary online learning management system", "https://moodle.stfx.ca/", "Academics", false));
        allLinks.add(new Models.LinkItem("az_library", "Library", "Angus L. Macdonald Library resources and research support", "https://www.stfx.ca/library", "Library", false));
        allLinks.add(new Models.LinkItem("az_recreation", "Recreation", "Saputo Centre fitness, pool schedule, intramural sports", "https://goxgo.ca/recreation/index", "Athletics", false));
        allLinks.add(new Models.LinkItem("az_registrar", "Registrar's Office", "Transcripts, enrollment verifications, convocation, dates", "https://www.stfx.ca/applications-admissions/registrars-office", "Academics", false));
        allLinks.add(new Models.LinkItem("az_safety", "Safety & Security", "Campus security patrols, incident reporting, 902-867-4444", "https://www.stfx.ca/safety-security", "Safety", false));
        allLinks.add(new Models.LinkItem("az_service_learning", "Service Learning", "Community service learning courses and immersion trips", "https://www.stfx.ca/programs-courses/service-learning", "Academics", false));
        allLinks.add(new Models.LinkItem("az_sexual_violence", "Sexual Violence Prevention & Response", "Confidential support, trauma-informed care, resources", "https://www.stfx.ca/student-services/support-services/visible-at-x", "Support", false));
        allLinks.add(new Models.LinkItem("az_alerts", "Sign up for StFX Alerts", "Campus emergency notification system (SMS and email)", "https://www.stfx.ca/safety-security/security/stfx-alerts", "Safety", false));
        allLinks.add(new Models.LinkItem("az_online", "StFX Online", "Online learning, continuing education, certificate courses", "https://www.stfx.ca/programs-courses/stfx-online", "Academics", false));
        allLinks.add(new Models.LinkItem("az_store", "StFX Store", "Textbooks, university clothing, gifts, supplies", "https://shop.stfx.ca/", "Services", false));
        allLinks.add(new Models.LinkItem("az_accounts", "Student Accounts", "Tuition fees, payment plans, refunds, tax forms (T2202)", "https://www.stfx.ca/student-accounts", "Financial", false));
        allLinks.add(new Models.LinkItem("az_career", "Student Career Centre", "Career counselling, job fairs, mock interviews", "https://www.stfx.ca/student-services/support-services/student-career-centre", "Careers", false));
        allLinks.add(new Models.LinkItem("az_theu", "TheU (Students' Union)", "Student representative council, societies, Golden X Inn", "https://www.theu.ca/", "Community", false));
        allLinks.add(new Models.LinkItem("az_theatre", "Theatre Antigonish", "Campus theatre productions, auditions, and performances", "http://festivalantigonish.com/theatreantigonish/", "Culture", false));
        allLinks.add(new Models.LinkItem("az_housing", "University Housing", "Residence buildings, room applications, move-in info", "https://www.stfx.ca/student-services/university-housing", "Housing", false));
        allLinks.add(new Models.LinkItem("az_vp_students", "VP, Students", "Office of the Vice-President, Student Experience", "https://www.stfx.ca/about/vice-president-students", "Administration", false));
        allLinks.add(new Models.LinkItem("az_wellspring", "Wellspring Centre", "Drop-in hospitality, relaxation, tea and conversation", "https://www.stfx.ca/student-services/support-services/wellspring", "Support", false));
    }

    public synchronized List<Models.WeeklyHighlight> getWeeklyHighlights() {
        return new ArrayList<>(weeklyHighlights);
    }

    public synchronized void setWeeklyHighlights(List<Models.WeeklyHighlight> list) {
        if (list != null && !list.isEmpty()) {
            this.weeklyHighlights.clear();
            this.weeklyHighlights.addAll(list);
            notifyDataChanged();
        }
    }

    public synchronized List<Models.EventItem> getEvents() {
        return new ArrayList<>(events);
    }

    public synchronized void setEvents(List<Models.EventItem> list) {
        if (list != null && !list.isEmpty()) {
            this.events.clear();
            this.events.addAll(list);
            notifyDataChanged();
        }
    }

    public synchronized void appendEvents(List<Models.EventItem> more) {
        if (more == null || more.isEmpty()) return;
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Models.EventItem item : this.events) {
            existing.add(item.eventUrl != null ? item.eventUrl : item.title);
        }
        boolean added = false;
        for (Models.EventItem item : more) {
            String key = item.eventUrl != null ? item.eventUrl : item.title;
            if (!existing.contains(key)) {
                this.events.add(item);
                existing.add(key);
                added = true;
            }
        }
        if (added) notifyDataChanged();
    }

    public synchronized List<Models.NewsItem> getNewsItems() {
        return new ArrayList<>(newsItems);
    }

    public synchronized void setNewsItems(List<Models.NewsItem> list) {
        if (list != null && !list.isEmpty()) {
            this.newsItems.clear();
            this.newsItems.addAll(list);
            notifyDataChanged();
        }
    }

    public synchronized void clearNewsItems() {
        this.newsItems.clear();
        notifyDataChanged();
    }

    public synchronized void appendNewsItems(List<Models.NewsItem> more) {
        if (more == null || more.isEmpty()) return;
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Models.NewsItem item : this.newsItems) {
            existing.add(item.url != null ? item.url : item.title);
        }
        boolean added = false;
        for (Models.NewsItem item : more) {
            String key = item.url != null ? item.url : item.title;
            if (!existing.contains(key)) {
                this.newsItems.add(item);
                existing.add(key);
                added = true;
            }
        }
        if (added) notifyDataChanged();
    }

    public synchronized List<Models.LinkItem> getAllLinks() {
        return getAlphabeticalLinks();
    }

    public synchronized List<Models.LinkItem> getQuickLinks() {
        List<Models.LinkItem> result = new ArrayList<>();
        for (Models.LinkItem item : allLinks) {
            if (item.isQuickLink) {
                result.add(item);
            }
        }
        return result;
    }

    public synchronized List<Models.LinkItem> getAlphabeticalLinks() {
        List<Models.LinkItem> result = new ArrayList<>(allLinks);
        Collections.sort(result, Comparator.comparing(a -> a.title.toLowerCase()));
        return result;
    }

    public synchronized List<Models.ProductItem> getProducts() {
        return new ArrayList<>(products);
    }

    public synchronized void setProducts(List<Models.ProductItem> list) {
        if (list != null && !list.isEmpty()) {
            this.products.clear();
            this.products.addAll(list);
            notifyDataChanged();
        }
    }

    public synchronized Models.BroadcastVideo getBroadcastVideo() {
        return broadcastVideo;
    }

    public synchronized void setBroadcastVideo(Models.BroadcastVideo video) {
        if (video != null) {
            this.broadcastVideo = video;
            notifyDataChanged();
        }
    }
}
