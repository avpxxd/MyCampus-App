package ca.stfx.mycampus.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ca.stfx.mycampus.R;
import ca.stfx.mycampus.data.DataRepository;
import ca.stfx.mycampus.data.Models;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class MyCampusFragment extends Fragment {

    private boolean isQuickLinksSelected = true;
    private String selectedLetter = "ALL";
    private String currentSearchQuery = "";

    private TextView tabQuickLinks;
    private TextView tabAzLinks;
    private TextView tvSectionCaption;
    private HorizontalScrollView scrollAlphabetFilters;
    private LinearLayout layoutAlphabetChips;
    private LinearLayout containerLinks;
    private TextView tvNoResults;
    private EditText etSearch;
    private ImageButton btnClearSearch;

    private final DataRepository.OnDataChangeListener dataListener = () -> {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::renderLinks);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_mycampus, container, false);

        tabQuickLinks = view.findViewById(R.id.tab_quick_links);
        tabAzLinks = view.findViewById(R.id.tab_az_links);
        tvSectionCaption = view.findViewById(R.id.tv_section_caption);
        scrollAlphabetFilters = view.findViewById(R.id.scroll_alphabet_filters);
        layoutAlphabetChips = view.findViewById(R.id.layout_alphabet_chips);
        containerLinks = view.findViewById(R.id.container_links);
        tvNoResults = view.findViewById(R.id.tv_no_results);
        etSearch = view.findViewById(R.id.et_search_mycampus);
        btnClearSearch = view.findViewById(R.id.btn_clear_search);

        setupTabs();
        setupAlphabetChips();
        setupSearch();
        renderLinks();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        DataRepository.getInstance().addListener(dataListener);
        renderLinks();
    }

    @Override
    public void onPause() {
        super.onPause();
        DataRepository.getInstance().removeListener(dataListener);
    }

    private void setupTabs() {
        tabQuickLinks.setOnClickListener(v -> {
            if (!isQuickLinksSelected) {
                isQuickLinksSelected = true;
                updateTabStyles();
                renderLinks();
            }
        });

        tabAzLinks.setOnClickListener(v -> {
            if (isQuickLinksSelected) {
                isQuickLinksSelected = false;
                updateTabStyles();
                renderLinks();
            }
        });
    }

    private void updateTabStyles() {
        if (isQuickLinksSelected) {
            tabQuickLinks.setBackgroundResource(R.drawable.bg_chip_selected);
            tabQuickLinks.setTextColor(getResources().getColor(R.color.stfx_white, null));
            tabAzLinks.setBackground(null);
            tabAzLinks.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvSectionCaption.setText("Frequently used campus portals and student services");
            scrollAlphabetFilters.setVisibility(View.GONE);
        } else {
            tabAzLinks.setBackgroundResource(R.drawable.bg_chip_selected);
            tabAzLinks.setTextColor(getResources().getColor(R.color.stfx_white, null));
            tabQuickLinks.setBackground(null);
            tabQuickLinks.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvSectionCaption.setText("Complete alphabetical index of StFX services and departments");
            scrollAlphabetFilters.setVisibility(View.VISIBLE);
        }
    }

    private void setupAlphabetChips() {
        layoutAlphabetChips.removeAllViews();
        String[] letters = new String[]{"ALL", "A", "B", "C", "D", "E", "F", "G", "H", "I", "K", "L", "M", "N", "O", "P", "R", "S", "W", "X"};

        for (String letter : letters) {
            TextView chip = new TextView(requireContext());
            chip.setText(letter);
            chip.setTextSize(12);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
            chip.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    letter.equals("ALL") ? dpToPx(48) : dpToPx(36), dpToPx(32));
            lp.setMargins(0, 0, dpToPx(6), 0);
            chip.setLayoutParams(lp);

            if (letter.equals(selectedLetter)) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected);
                chip.setTextColor(getResources().getColor(R.color.stfx_white, null));
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                chip.setTextColor(getResources().getColor(R.color.text_primary, null));
            }

            chip.setOnClickListener(v -> {
                selectedLetter = letter;
                setupAlphabetChips();
                renderLinks();
            });

            layoutAlphabetChips.addView(chip);
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString().trim().toLowerCase();
                btnClearSearch.setVisibility(currentSearchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                renderLinks();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnClearSearch.setOnClickListener(v -> {
            etSearch.setText("");
            currentSearchQuery = "";
        });
    }

    private void renderLinks() {
        containerLinks.removeAllViews();
        List<Models.LinkItem> sourceList = isQuickLinksSelected
                ? DataRepository.getInstance().getQuickLinks()
                : DataRepository.getInstance().getAllLinks();

        List<Models.LinkItem> filtered = new ArrayList<>();
        for (Models.LinkItem item : sourceList) {
            // Apply alphabet filter if in A-Z mode
            if (!isQuickLinksSelected && !selectedLetter.equals("ALL")) {
                if (!item.letter.equalsIgnoreCase(selectedLetter)) {
                    continue;
                }
            }
            // Apply search query
            if (!currentSearchQuery.isEmpty()) {
                boolean matchesTitle = item.title.toLowerCase().contains(currentSearchQuery);
                boolean matchesSubtitle = item.subtitle.toLowerCase().contains(currentSearchQuery);
                boolean matchesCategory = item.category.toLowerCase().contains(currentSearchQuery);
                if (!matchesTitle && !matchesSubtitle && !matchesCategory) {
                    continue;
                }
            }
            filtered.add(item);
        }

        if (filtered.isEmpty()) {
            tvNoResults.setVisibility(View.VISIBLE);
            return;
        } else {
            tvNoResults.setVisibility(View.GONE);
        }

        String lastHeader = "";
        for (Models.LinkItem item : filtered) {
            // In A-Z mode, show alphabetical section headers
            if (!isQuickLinksSelected && (selectedLetter.equals("ALL") || currentSearchQuery.length() > 0)) {
                String currentHeader = item.letter.toUpperCase();
                if (!currentHeader.equals(lastHeader)) {
                    lastHeader = currentHeader;
                    TextView tvHeader = new TextView(requireContext());
                    tvHeader.setText(lastHeader);
                    tvHeader.setTextSize(14);
                    tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvHeader.setTextColor(getResources().getColor(R.color.stfx_blue, null));
                    tvHeader.setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(4));
                    containerLinks.addView(tvHeader);
                }
            }

            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dpToPx(10));
            card.setLayoutParams(lp);
            card.setRadius(dpToPx(12));
            card.setCardElevation(dpToPx(1));
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            card.setStrokeColor(getResources().getColor(R.color.divider_color, null));
            card.setStrokeWidth(dpToPx(1));

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

            // Letter / Category Avatar
            TextView tvAvatar = new TextView(requireContext());
            tvAvatar.setText(item.letter);
            tvAvatar.setTextSize(16);
            tvAvatar.setTypeface(null, android.graphics.Typeface.BOLD);
            tvAvatar.setGravity(android.view.Gravity.CENTER);
            tvAvatar.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvAvatar.setBackgroundResource(R.drawable.bg_badge_gold);
            LinearLayout.LayoutParams avatarLp = new LinearLayout.LayoutParams(dpToPx(38), dpToPx(38));
            avatarLp.setMargins(0, 0, dpToPx(12), 0);
            tvAvatar.setLayoutParams(avatarLp);
            row.addView(tvAvatar);

            // Title and Subtitle
            LinearLayout textBlock = new LinearLayout(requireContext());
            textBlock.setOrientation(LinearLayout.VERTICAL);
            textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout titleRow = new LinearLayout(requireContext());
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(item.title);
            tvTitle.setTextSize(15);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            titleRow.addView(tvTitle);

            textBlock.addView(titleRow);

            TextView tvSubtitle = new TextView(requireContext());
            tvSubtitle.setText(item.subtitle);
            tvSubtitle.setTextSize(12);
            tvSubtitle.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvSubtitle.setPadding(0, dpToPx(2), 0, 0);
            textBlock.addView(tvSubtitle);

            row.addView(textBlock);

            // Open icon on right
            ImageView ivArrow = new ImageView(requireContext());
            ivArrow.setImageResource(R.drawable.ic_open_in_browser);
            ivArrow.setColorFilter(getResources().getColor(R.color.stfx_blue, null));
            LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
            arrowLp.setMargins(dpToPx(8), 0, 0, 0);
            ivArrow.setLayoutParams(arrowLp);
            row.addView(ivArrow);

            card.addView(row);

            card.setOnClickListener(v -> WebViewerActivity.open(requireContext(), item.url, item.title));

            containerLinks.addView(card);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
