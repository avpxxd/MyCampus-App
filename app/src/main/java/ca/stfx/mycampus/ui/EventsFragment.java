package ca.stfx.mycampus.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ca.stfx.mycampus.R;
import ca.stfx.mycampus.data.DataRepository;
import ca.stfx.mycampus.data.LiveSyncManager;
import ca.stfx.mycampus.data.Models;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;

public class EventsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout containerEvents;
    private TextView tvEventsCount;
    private TextView tvNoEvents;
    private EditText etSearch;

    private String currentQuery = "";

    private final DataRepository.OnDataChangeListener dataListener = () -> {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::renderEvents);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_events, container, false);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_events);
        containerEvents = view.findViewById(R.id.container_all_events);
        tvEventsCount = view.findViewById(R.id.tv_events_count);
        tvNoEvents = view.findViewById(R.id.tv_no_events);
        etSearch = view.findViewById(R.id.et_search_events);

        setupSearch();

        swipeRefresh.setColorSchemeResources(R.color.stfx_navy, R.color.stfx_gold);
        swipeRefresh.setOnRefreshListener(() -> {
            LiveSyncManager.getInstance().syncAll(requireContext(), success -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        renderEvents();
                    });
                }
            });
        });

        // Show loading state immediately, then trigger a live sync
        tvEventsCount.setText("Loading events from stfx.ca…");
        tvNoEvents.setVisibility(View.GONE);
        if (DataRepository.getInstance().getEvents().isEmpty()) {
            swipeRefresh.setRefreshing(true);
            LiveSyncManager.getInstance().syncAll(requireContext(), success -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        renderEvents();
                    });
                }
            });
        } else {
            renderEvents();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        DataRepository.getInstance().addListener(dataListener);
        renderEvents();
    }

    @Override
    public void onPause() {
        super.onPause();
        DataRepository.getInstance().removeListener(dataListener);
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s.toString().trim().toLowerCase();
                renderEvents();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void renderEvents() {
        if (!isAdded()) return;
        containerEvents.removeAllViews();
        List<Models.EventItem> all = DataRepository.getInstance().getEvents();
        List<Models.EventItem> filtered = new ArrayList<>();

        for (Models.EventItem item : all) {
            // Search query check
            if (!currentQuery.isEmpty()) {
                boolean matchesTitle = item.title.toLowerCase().contains(currentQuery);
                boolean matchesDesc = item.description != null && item.description.toLowerCase().contains(currentQuery);
                boolean matchesLoc = item.location != null && item.location.toLowerCase().contains(currentQuery);
                if (!matchesTitle && !matchesDesc && !matchesLoc) {
                    continue;
                }
            }
            filtered.add(item);
        }

        tvEventsCount.setText("Showing " + filtered.size() + " upcoming StFX events");

        if (filtered.isEmpty()) {
            if (currentQuery.isEmpty()) {
                tvNoEvents.setText("⚠️ Could not load events from stfx.ca\nPull down to try again.");
            } else {
                tvNoEvents.setText("No events match \"" + currentQuery + "\"");
            }
            tvNoEvents.setVisibility(View.VISIBLE);
            return;
        } else {
            tvNoEvents.setVisibility(View.GONE);
        }

        for (Models.EventItem event : filtered) {
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
            content.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));

            // Main row: Calendar Date Badge (Left) + Details (Right)
            LinearLayout topSection = new LinearLayout(requireContext());
            topSection.setOrientation(LinearLayout.HORIZONTAL);

            // Real Calendar Page Badge (Properly Proportioned)
            LinearLayout dateBadge = new LinearLayout(requireContext());
            dateBadge.setOrientation(LinearLayout.VERTICAL);
            dateBadge.setBackgroundResource(R.drawable.bg_calendar_badge);
            LinearLayout.LayoutParams dateBadgeLp = new LinearLayout.LayoutParams(dpToPx(56), dpToPx(64));
            dateBadge.setLayoutParams(dateBadgeLp);

            // Month header strip (Navy)
            TextView tvMonth = new TextView(requireContext());
            tvMonth.setText(event.dateMonth != null ? event.dateMonth.toUpperCase() : "SEP");
            tvMonth.setTextSize(11);
            tvMonth.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMonth.setTextColor(getResources().getColor(R.color.stfx_gold, null));
            tvMonth.setGravity(android.view.Gravity.CENTER);
            tvMonth.setBackgroundResource(R.drawable.bg_calendar_header);
            LinearLayout.LayoutParams monthLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(22));
            tvMonth.setLayoutParams(monthLp);
            dateBadge.addView(tvMonth);

            // Day number (White body with navy text)
            TextView tvDay = new TextView(requireContext());
            tvDay.setText(event.dateDay != null ? event.dateDay : "25");
            tvDay.setTextSize(20);
            tvDay.setTypeface(null, android.graphics.Typeface.BOLD);
            tvDay.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvDay.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams dayLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(42));
            tvDay.setLayoutParams(dayLp);
            dateBadge.addView(tvDay);

            topSection.addView(dateBadge);

            // Info Column
            LinearLayout infoCol = new LinearLayout(requireContext());
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoColLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            infoColLp.setMargins(dpToPx(14), 0, 0, 0);
            infoCol.setLayoutParams(infoColLp);

            // Category & Today Chips
            LinearLayout catRow = new LinearLayout(requireContext());
            catRow.setOrientation(LinearLayout.HORIZONTAL);
            catRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvCategory = new TextView(requireContext());
            tvCategory.setText(event.category.toUpperCase());
            tvCategory.setTextSize(10);
            tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCategory.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvCategory.setBackgroundResource(R.drawable.bg_badge_gold);
            tvCategory.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            catRow.addView(tvCategory);

            if (event.isToday) {
                TextView tvTodayChip = new TextView(requireContext());
                tvTodayChip.setText("TODAY");
                tvTodayChip.setTextSize(10);
                tvTodayChip.setTypeface(null, android.graphics.Typeface.BOLD);
                tvTodayChip.setTextColor(getResources().getColor(R.color.stfx_white, null));
                tvTodayChip.setBackgroundResource(R.drawable.bg_badge_navy);
                tvTodayChip.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
                LinearLayout.LayoutParams tLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tLp.setMargins(dpToPx(6), 0, 0, 0);
                tvTodayChip.setLayoutParams(tLp);
                catRow.addView(tvTodayChip);
            }

            infoCol.addView(catRow);

            // Event Title
            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(event.title);
            tvTitle.setTextSize(15);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvTitle.setPadding(0, dpToPx(4), 0, dpToPx(2));
            infoCol.addView(tvTitle);

            // Date & Time Line
            TextView tvTime = new TextView(requireContext());
            tvTime.setText("🕒 " + (event.formattedDate != null ? event.formattedDate : event.time));
            tvTime.setTextSize(12);
            tvTime.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTime.setTextColor(getResources().getColor(R.color.stfx_blue, null));
            tvTime.setPadding(0, 0, 0, dpToPx(2));
            infoCol.addView(tvTime);

            // Location Line
            TextView tvLocation = new TextView(requireContext());
            tvLocation.setText("📍 " + event.location);
            tvLocation.setTextSize(12);
            tvLocation.setTextColor(getResources().getColor(R.color.text_secondary, null));
            infoCol.addView(tvLocation);

            topSection.addView(infoCol);
            content.addView(topSection);

            // Action Buttons Row (Add to Calendar, Share, View Webpage)
            LinearLayout actionsRow = new LinearLayout(requireContext());
            actionsRow.setOrientation(LinearLayout.HORIZONTAL);
            actionsRow.setPadding(0, dpToPx(10), 0, 0);

            MaterialButton btnCal = new MaterialButton(requireContext());
            btnCal.setText("Add to Calendar");
            btnCal.setTextSize(11);
            btnCal.setIconResource(R.drawable.ic_calendar_add);
            btnCal.setIconTint(getResources().getColorStateList(R.color.stfx_white, null));
            btnCal.setBackgroundColor(getResources().getColor(R.color.stfx_navy, null));
            btnCal.setTextColor(getResources().getColor(R.color.stfx_white, null));
            btnCal.setCornerRadius(dpToPx(8));
            LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(0, dpToPx(38), 1f);
            calLp.setMargins(0, 0, dpToPx(6), 0);
            btnCal.setLayoutParams(calLp);
            btnCal.setOnClickListener(v -> addToCalendar(event));
            actionsRow.addView(btnCal);

            MaterialButton btnShare = new MaterialButton(requireContext());
            btnShare.setText("Share");
            btnShare.setTextSize(11);
            btnShare.setIconResource(R.drawable.ic_share);
            btnShare.setIconTint(getResources().getColorStateList(R.color.stfx_navy, null));
            btnShare.setBackgroundColor(getResources().getColor(R.color.stfx_gold_pale, null));
            btnShare.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            btnShare.setCornerRadius(dpToPx(8));
            btnShare.setPadding(dpToPx(6), 0, dpToPx(6), 0);
            LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(dpToPx(104), dpToPx(38));
            btnShare.setLayoutParams(shareLp);
            btnShare.setOnClickListener(v -> shareEvent(event));
            actionsRow.addView(btnShare);

            content.addView(actionsRow);
            card.addView(content);

            // Clicking the card opens the comprehensive dialog (user liked event popup)
            card.setOnClickListener(v -> showEventDetails(event));

            containerEvents.addView(card);
        }
    }

    private void showEventDetails(Models.EventItem event) {
        new AlertDialog.Builder(requireContext())
                .setTitle(event.title)
                .setMessage("Category: " + event.category
                        + "\nDate: " + event.formattedDate
                        + "\nLocation: " + event.location
                        + "\n\n" + event.description)
                .setPositiveButton("Add to Calendar", (dialog, which) -> addToCalendar(event))
                .setNeutralButton("Visit Event Page", (dialog, which) -> {
                    String url = (event.eventUrl != null && !event.eventUrl.isEmpty())
                            ? event.eventUrl : "https://www.stfx.ca/events";
                    WebViewerActivity.open(requireContext(), url, event.title);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void addToCalendar(Models.EventItem event) {
        Intent calIntent = new Intent(Intent.ACTION_INSERT);
        calIntent.setData(CalendarContract.Events.CONTENT_URI);
        calIntent.putExtra(CalendarContract.Events.TITLE, event.title + " (StFX)");
        calIntent.putExtra(CalendarContract.Events.EVENT_LOCATION, event.location);
        calIntent.putExtra(CalendarContract.Events.DESCRIPTION, event.description + "\n\nInfo: " + event.eventUrl);
        startActivity(calIntent);
    }

    private void shareEvent(Models.EventItem event) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, event.title);
        shareIntent.putExtra(Intent.EXTRA_TEXT, event.title + "\nWhen: " + event.formattedDate + "\nWhere: " + event.location + "\n\nDetails: " + event.eventUrl);
        startActivity(Intent.createChooser(shareIntent, "Share Event"));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
