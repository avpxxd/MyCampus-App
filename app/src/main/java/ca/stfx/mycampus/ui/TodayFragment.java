package ca.stfx.mycampus.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ca.stfx.mycampus.MainActivity;
import ca.stfx.mycampus.R;
import ca.stfx.mycampus.data.DataRepository;
import ca.stfx.mycampus.data.LiveSyncManager;
import ca.stfx.mycampus.data.Models;
import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class TodayFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout containerThisWeek;
    private LinearLayout containerTodaysEvents;
    private TextView tvGreetingDate;
    private TextView tvGreetingWeather;
    private TextView tvVideoTitle;

    private final DataRepository.OnDataChangeListener dataListener = () -> {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::loadContent);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_today, container, false);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_today);
        containerThisWeek = view.findViewById(R.id.container_this_week);
        containerTodaysEvents = view.findViewById(R.id.container_todays_events);
        tvGreetingDate = view.findViewById(R.id.tv_greeting_date);
        tvGreetingWeather = view.findViewById(R.id.tv_greeting_weather);
        tvVideoTitle = view.findViewById(R.id.tv_video_title);

        updateDate();
        fetchLiveWeather();

        // User requirement: Clicking the campus broadcast takes you directly to the YouTube video!
        View btnPlayVideo = view.findViewById(R.id.btn_play_video);
        View ivPlayButton = view.findViewById(R.id.iv_play_button);
        View layoutVideoPreview = view.findViewById(R.id.layout_video_preview);
        
        View.OnClickListener playListener = v -> openCampusBroadcast();
        btnPlayVideo.setOnClickListener(playListener);
        ivPlayButton.setOnClickListener(playListener);
        layoutVideoPreview.setOnClickListener(playListener);
        view.findViewById(R.id.tv_btn_view_all_events).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).selectTab(R.id.nav_events);
            }
        });

        swipeRefresh.setColorSchemeResources(R.color.stfx_navy, R.color.stfx_gold);
        swipeRefresh.setOnRefreshListener(() -> {
            updateDate();
            fetchLiveWeather();
            LiveSyncManager.getInstance().syncAll(requireContext(), success -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        loadContent();
                    });
                }
            });
        });

        loadContent();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        DataRepository.getInstance().addListener(dataListener);
        updateDate();
        fetchLiveWeather();
        loadContent();
    }

    private void updateDate() {
        if (tvGreetingDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("'📅' EEEE, MMMM d, yyyy", Locale.CANADA);
            tvGreetingDate.setText(sdf.format(new Date()));
        }
    }

    private void fetchLiveWeather() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=45.6265&longitude=-61.9926&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=America%2FHalifax");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7a)");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject current = json.getJSONObject("current");
                    double temp = current.getDouble("temperature_2m");
                    int code = current.getInt("weather_code");

                    JSONObject daily = json.getJSONObject("daily");
                    JSONArray maxArr = daily.getJSONArray("temperature_2m_max");
                    JSONArray minArr = daily.getJSONArray("temperature_2m_min");
                    double maxTemp = maxArr.getDouble(0);
                    double minTemp = minArr.getDouble(0);

                    WeatherInfo info = getWeatherDescription(code);
                    String weatherText = String.format(Locale.CANADA,
                            "%s Antigonish, NS • %.0f°C %s • High %.0f° / Low %.0f°",
                            info.emoji, temp, info.description, maxTemp, minTemp);

                    if (isAdded()) {
                        requireActivity().runOnUiThread(() -> {
                            if (tvGreetingWeather != null) {
                                tvGreetingWeather.setText(weatherText);
                            }
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private static class WeatherInfo {
        final String emoji;
        final String description;
        WeatherInfo(String emoji, String description) {
            this.emoji = emoji;
            this.description = description;
        }
    }

    private WeatherInfo getWeatherDescription(int code) {
        switch (code) {
            case 0:
                return new WeatherInfo("☀️", "Clear Sky");
            case 1:
                return new WeatherInfo("🌤️", "Mainly Clear");
            case 2:
                return new WeatherInfo("⛅", "Partly Cloudy");
            case 3:
                return new WeatherInfo("☁️", "Overcast");
            case 45:
            case 48:
                return new WeatherInfo("🌫️", "Foggy");
            case 51:
            case 53:
            case 55:
                return new WeatherInfo("🌦️", "Light Drizzle");
            case 56:
            case 57:
                return new WeatherInfo("🌨️", "Freezing Drizzle");
            case 61:
            case 63:
                return new WeatherInfo("🌧️", "Rain");
            case 65:
                return new WeatherInfo("🌧️", "Heavy Rain");
            case 66:
            case 67:
                return new WeatherInfo("🌨️", "Freezing Rain");
            case 71:
            case 73:
                return new WeatherInfo("🌨️", "Snow Fall");
            case 75:
            case 77:
                return new WeatherInfo("❄️", "Heavy Snow");
            case 80:
            case 81:
            case 82:
                return new WeatherInfo("🌧️", "Rain Showers");
            case 85:
            case 86:
                return new WeatherInfo("🌨️", "Snow Showers");
            case 95:
                return new WeatherInfo("⛈️", "Thunderstorm");
            case 96:
            case 99:
                return new WeatherInfo("⛈️", "Severe Thunderstorm");
            default:
                return new WeatherInfo("🌤️", "Partly Cloudy");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        DataRepository.getInstance().removeListener(dataListener);
    }

    private void openCampusBroadcast() {
        Models.BroadcastVideo video = DataRepository.getInstance().getBroadcastVideo();
        if (video == null || video.videoUrl == null || video.videoUrl.isEmpty()) {
            Toast.makeText(getContext(), "⚠️ Broadcast not yet loaded — pull to refresh", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(video.videoUrl));
            startActivity(intent);
        } catch (Exception e) {
            WebViewerActivity.open(requireContext(), video.videoUrl, "MyCampus Today Broadcast");
        }
    }

    private void loadContent() {
        if (!isAdded()) return;

        Models.BroadcastVideo video = DataRepository.getInstance().getBroadcastVideo();
        if (video != null && tvVideoTitle != null) {
            tvVideoTitle.setText(video.title);
        } else if (tvVideoTitle != null) {
            tvVideoTitle.setText("MyCampus Today — Loading...");
        }

        populateThisWeek();
        populateTodaysEvents();
    }

    private void populateThisWeek() {
        containerThisWeek.removeAllViews();
        List<Models.WeeklyHighlight> highlights = DataRepository.getInstance().getWeeklyHighlights();

        if (highlights.isEmpty()) {
            TextView tvError = new TextView(requireContext());
            tvError.setText("Loading announcements, please wait...");
            tvError.setTextSize(13);
            tvError.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvError.setPadding(dpToPx(4), dpToPx(8), dpToPx(4), dpToPx(8));
            containerThisWeek.addView(tvError);
            return;
        }

        for (Models.WeeklyHighlight item : highlights) {
            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dpToPx(12));
            card.setLayoutParams(lp);
            card.setRadius(dpToPx(14));
            card.setCardElevation(dpToPx(2));
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            card.setStrokeColor(getResources().getColor(R.color.divider_color, null));
            card.setStrokeWidth(dpToPx(1));

            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);

            // Announcement image if available
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                ImageView ivAnnouncement = new ImageView(requireContext());
                LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(140));
                ivAnnouncement.setLayoutParams(imgLp);
                ivAnnouncement.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivAnnouncement.setBackgroundColor(0xFF00143F);
                Glide.with(this)
                        .load(item.imageUrl)
                        .placeholder(R.drawable.bg_video_thumbnail)
                        .into(ivAnnouncement);
                content.addView(ivAnnouncement);
            }

            LinearLayout textContent = new LinearLayout(requireContext());
            textContent.setOrientation(LinearLayout.VERTICAL);
            textContent.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(14));

            // Top tag row
            LinearLayout tagRow = new LinearLayout(requireContext());
            tagRow.setOrientation(LinearLayout.HORIZONTAL);
            tagRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvCategory = new TextView(requireContext());
            tvCategory.setText(item.category.toUpperCase());
            tvCategory.setTextSize(10);
            tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCategory.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvCategory.setBackgroundResource(R.drawable.bg_badge_gold);
            tvCategory.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            tagRow.addView(tvCategory);

            TextView tvDate = new TextView(requireContext());
            tvDate.setText(item.dateRange);
            tvDate.setTextSize(11);
            tvDate.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvDate.setPadding(dpToPx(8), 0, 0, 0);
            tagRow.addView(tvDate);

            textContent.addView(tagRow);

            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(item.title);
            tvTitle.setTextSize(15);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvTitle.setPadding(0, dpToPx(6), 0, dpToPx(2));
            textContent.addView(tvTitle);

            if (item.location != null && !item.location.isEmpty()) {
                TextView tvLocation = new TextView(requireContext());
                tvLocation.setText("📍 " + item.location);
                tvLocation.setTextSize(12);
                tvLocation.setTextColor(getResources().getColor(R.color.stfx_blue, null));
                tvLocation.setPadding(0, 0, 0, dpToPx(4));
                textContent.addView(tvLocation);
            }

            TextView tvDesc = new TextView(requireContext());
            tvDesc.setText(item.description);
            tvDesc.setTextSize(13);
            tvDesc.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvDesc.setMaxLines(3);
            tvDesc.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tvDesc.setLineSpacing(dpToPx(2), 1f);
            textContent.addView(tvDesc);

            TextView tvMoreHint = new TextView(requireContext());
            tvMoreHint.setText("Tap to read full announcement →");
            tvMoreHint.setTextSize(11);
            tvMoreHint.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMoreHint.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvMoreHint.setPadding(0, dpToPx(6), 0, 0);
            textContent.addView(tvMoreHint);

            content.addView(textContent);
            card.addView(content);

            card.setOnClickListener(v -> showAnnouncementDetails(item));

            containerThisWeek.addView(card);
        }
    }

    private void showAnnouncementDetails(Models.WeeklyHighlight item) {
        new AlertDialog.Builder(requireContext())
                .setTitle(item.title)
                .setMessage("Date: " + item.dateRange
                        + "\nLocation: " + item.location
                        + "\n\n" + item.description)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share", (dialog, which) -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.title);
                    shareIntent.putExtra(Intent.EXTRA_TEXT, item.title + "\nWhen: " + item.dateRange + "\nWhere: " + item.location + "\n\n" + item.description);
                    startActivity(Intent.createChooser(shareIntent, "Share Announcement"));
                })
                .show();
    }

    private void populateTodaysEvents() {
        containerTodaysEvents.removeAllViews();
        List<Models.EventItem> events = DataRepository.getInstance().getEvents();

        int count = 0;
        for (Models.EventItem event : events) {
            if (!event.isToday && count >= 3) continue;
            count++;

            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dpToPx(10));
            card.setLayoutParams(lp);
            card.setRadius(dpToPx(12));
            card.setCardElevation(dpToPx(2));
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            card.setStrokeColor(getResources().getColor(R.color.divider_color, null));
            card.setStrokeWidth(dpToPx(1));

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

            // Calendar Badge on left
            LinearLayout dateBadge = new LinearLayout(requireContext());
            dateBadge.setOrientation(LinearLayout.VERTICAL);
            dateBadge.setBackgroundResource(R.drawable.bg_calendar_badge);
            LinearLayout.LayoutParams dateBadgeLp = new LinearLayout.LayoutParams(dpToPx(52), dpToPx(58));
            dateBadge.setLayoutParams(dateBadgeLp);

            TextView tvMonth = new TextView(requireContext());
            tvMonth.setText(event.dateMonth != null ? event.dateMonth.toUpperCase() : "SEP");
            tvMonth.setTextSize(10);
            tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMonth.setTextColor(getResources().getColor(R.color.stfx_gold, null));
            tvMonth.setGravity(android.view.Gravity.CENTER);
            tvMonth.setBackgroundResource(R.drawable.bg_calendar_header);
            LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(20));
            tvMonth.setLayoutParams(monthLp);
            dateBadge.addView(tvMonth);

            TextView tvDay = new TextView(requireContext());
            tvDay.setText(event.dateDay != null ? event.dateDay : "25");
            tvDay.setTextSize(18);
            tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDay.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvDay.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(38));
            tvDay.setLayoutParams(dayLp);
            dateBadge.addView(tvDay);

            row.addView(dateBadge);

            // Info on right
            LinearLayout rightBlock = new LinearLayout(requireContext());
            rightBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            rightLp.setMargins(dpToPx(12), 0, 0, 0);
            rightBlock.setLayoutParams(rightLp);

            TextView tvCategory = new TextView(requireContext());
            tvCategory.setText(event.category.toUpperCase());
            tvCategory.setTextSize(10);
            tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCategory.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvCategory.setBackgroundResource(R.drawable.bg_badge_gold);
            tvCategory.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tvCategory.setLayoutParams(catLp);
            rightBlock.addView(tvCategory);

            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(event.title);
            tvTitle.setTextSize(14);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvTitle.setPadding(0, dpToPx(4), 0, dpToPx(2));
            rightBlock.addView(tvTitle);

            TextView tvTime = new TextView(requireContext());
            tvTime.setText("🕒 " + (event.formattedDate != null ? event.formattedDate : event.time));
            tvTime.setTextSize(12);
            tvTime.setTextColor(getResources().getColor(R.color.text_secondary, null));
            rightBlock.addView(tvTime);

            TextView tvLocation = new TextView(requireContext());
            tvLocation.setText("📍 " + event.location);
            tvLocation.setTextSize(12);
            tvLocation.setTextColor(getResources().getColor(R.color.stfx_blue, null));
            rightBlock.addView(tvLocation);

            row.addView(rightBlock);
            card.addView(row);

            card.setOnClickListener(v -> showEventDetails(event));
            containerTodaysEvents.addView(card);
        }
    }



    private void showEventDetails(Models.EventItem event) {
        new AlertDialog.Builder(requireContext())
                .setTitle(event.title)
                .setMessage("Category: " + event.category
                        + "\nTime: " + (event.formattedDate != null ? event.formattedDate : event.time)
                        + "\nLocation: " + event.location
                        + "\n\n" + event.description)
                .setPositiveButton("Add to Calendar", (dialog, which) -> {
                    Intent calIntent = new Intent(Intent.ACTION_INSERT);
                    calIntent.setData(CalendarContract.Events.CONTENT_URI);
                    calIntent.putExtra(CalendarContract.Events.TITLE, event.title + " (StFX)");
                    calIntent.putExtra(CalendarContract.Events.EVENT_LOCATION, event.location);
                    calIntent.putExtra(CalendarContract.Events.DESCRIPTION, event.description + "\n\n" + event.eventUrl);
                    startActivity(calIntent);
                })
                .setNeutralButton("Visit Event Page", (dialog, which) -> {
                    String url = (event.eventUrl != null && !event.eventUrl.isEmpty())
                            ? event.eventUrl : "https://www.stfx.ca/events";
                    WebViewerActivity.open(requireContext(), url, event.title);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
