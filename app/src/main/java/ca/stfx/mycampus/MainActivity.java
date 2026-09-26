package ca.stfx.mycampus;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import ca.stfx.mycampus.ui.EventsFragment;
import ca.stfx.mycampus.ui.MyCampusFragment;
import ca.stfx.mycampus.ui.NewsFragment;
import ca.stfx.mycampus.ui.StoreFragment;
import ca.stfx.mycampus.ui.TodayFragment;
import ca.stfx.mycampus.ui.WebViewerActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;
    private TextView tvAppTitle;
    private TextView tvAppSubtitle;

    private final Fragment fragmentToday = new TodayFragment();
    private final Fragment fragmentMyCampus = new MyCampusFragment();
    private final Fragment fragmentEvents = new EventsFragment();
    private final Fragment fragmentNews = new NewsFragment();
    private final Fragment fragmentStore = new StoreFragment();

    private Fragment activeFragment = fragmentToday;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvAppTitle = findViewById(R.id.tv_app_title);
        tvAppSubtitle = findViewById(R.id.tv_app_subtitle);
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setItemActiveIndicatorEnabled(false);

        ImageButton btnInfo = findViewById(R.id.btn_info);

        // Setup fragments in FragmentManager
        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.fragment_container, fragmentStore, "store").hide(fragmentStore)
                .add(R.id.fragment_container, fragmentNews, "news").hide(fragmentNews)
                .add(R.id.fragment_container, fragmentEvents, "events").hide(fragmentEvents)
                .add(R.id.fragment_container, fragmentMyCampus, "mycampus").hide(fragmentMyCampus)
                .add(R.id.fragment_container, fragmentToday, "today")
                .commit();

        View topBar = findViewById(R.id.top_toolbar);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_today) {
                if (topBar != null) topBar.setVisibility(View.VISIBLE);
                switchFragment(fragmentToday, "StFX MyCampus", "Today @ X");
                return true;
            } else if (itemId == R.id.nav_mycampus) {
                if (topBar != null) topBar.setVisibility(View.VISIBLE);
                switchFragment(fragmentMyCampus, "MyCampus", "Quick Links & A-Z Directory");
                return true;
            } else if (itemId == R.id.nav_events) {
                if (topBar != null) topBar.setVisibility(View.VISIBLE);
                switchFragment(fragmentEvents, "Campus Events", "All Upcoming Activities");
                return true;
            } else if (itemId == R.id.nav_news) {
                if (topBar != null) topBar.setVisibility(View.VISIBLE);
                switchFragment(fragmentNews, "StFX News", "All Campus Stories");
                return true;
            } else if (itemId == R.id.nav_store) {
                if (topBar != null) topBar.setVisibility(View.GONE);
                switchFragment(fragmentStore, "St.FX Store", "Official Gear & Apparel");
                return true;
            }
            return false;
        });

        // Refresh button removed.

        btnInfo.setOnClickListener(v -> showAboutDialog());
        findViewById(R.id.fl_top_logo).setOnClickListener(v -> showAboutDialog());

        // Initial background sync for live events, announcements, news, video, and store
        ca.stfx.mycampus.data.LiveSyncManager.getInstance().syncAll(this, null);
    }

    @Override
    public void onBackPressed() {
        if (activeFragment == fragmentStore && ((StoreFragment) fragmentStore).canGoBack()) {
            ((StoreFragment) fragmentStore).goBack();
            return;
        }
        super.onBackPressed();
    }

    private void switchFragment(Fragment target, String title, String subtitle) {
        if (activeFragment != target) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit();
            activeFragment = target;
        }
        tvAppTitle.setText(title);
        tvAppSubtitle.setText(subtitle);
    }

    public void selectTab(int tabId) {
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(tabId);
        }
    }

    private void showAboutDialog() {
        TextView textView = new TextView(this);
        textView.setTextSize(15);
        textView.setPadding(60, 40, 60, 40);
        textView.setLineSpacing(0, 1.3f);
        
        String text = "St. Francis Xavier University<br>"
                + "Antigonish, Nova Scotia, Canada<br><br>"
                + "<b>Campus Assistance:</b><br>"
                + "Safety & Security: <a href=\"tel:9028674444\">(902) 867-4444</a><br>"
                + "Residence Life: <a href=\"tel:9028675856\">(902) 867-5856</a><br>"
                + "University Housing: <a href=\"tel:9028675106\">(902) 867-5106</a><br>"
                + "Student Life: <a href=\"tel:9028672276\">(902) 867-2276</a><br>"
                + "Facilities Desk: <a href=\"tel:9028672149\">(902) 867-2149</a><br>"
                + "IT Services: <a href=\"tel:9028672356\">(902) 867-2356</a><br><br>"
                + "<small>app version 0.5 Public Beta<br>"
                + "Bugs? email me: <a href=\"mailto:x2025edx@stfx.ca\">x2025edx@stfx.ca</a><br>"
                + "Update Your App: <a href=\"https://github.com/avpxxd/MyCampus-App\">github.com/avpxxd/MyCampus-App</a></small>";
                
        textView.setText(android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY));
        textView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        textView.setTextColor(getResources().getColor(android.R.color.black, null));
        
        new AlertDialog.Builder(this)
                .setTitle("About StFX MyCampus App")
                .setView(textView)
                .setPositiveButton("Close", null)
                .show();
    }
}
