package ca.stfx.mycampus.ui;

import android.os.Bundle;
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
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ca.stfx.mycampus.R;
import ca.stfx.mycampus.data.DataRepository;
import ca.stfx.mycampus.data.LiveSyncManager;
import ca.stfx.mycampus.data.Models;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

public class NewsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView scrollNews;
    private LinearLayout containerNews;
    private LinearLayout layoutLoadingMore;
    private MaterialButton btnLoadMore;
    private TextView tvNoNews;
    private EditText etSearch;

    private MaterialCardView cardFeaturedNews;
    private TextView tvFeaturedTitle;
    private TextView tvFeaturedDesc;
    private TextView tvFeaturedTime;

    private String currentQuery = "";
    private int currentNewsPage = 1;
    private boolean isLoadingMore = false;
    private boolean hasMorePages = true;

    private final DataRepository.OnDataChangeListener dataListener = () -> {
        if (isAdded()) {
            requireActivity().runOnUiThread(this::renderNews);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_news, container, false);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_news);
        scrollNews = view.findViewById(R.id.scroll_news);
        containerNews = view.findViewById(R.id.container_all_news);
        layoutLoadingMore = view.findViewById(R.id.layout_loading_more_news);
        btnLoadMore = view.findViewById(R.id.btn_load_more_news);
        tvNoNews = view.findViewById(R.id.tv_no_news);
        etSearch = view.findViewById(R.id.et_search_news);

        cardFeaturedNews = view.findViewById(R.id.card_featured_news);
        tvFeaturedTitle = view.findViewById(R.id.tv_featured_news_title);
        tvFeaturedDesc = view.findViewById(R.id.tv_featured_news_desc);
        tvFeaturedTime = view.findViewById(R.id.tv_featured_news_time);
        ImageView ivFeaturedImage = view.findViewById(R.id.iv_featured_news_image);
        cardFeaturedNews.setTag(ivFeaturedImage);

        setupSearch();
        setupPagination();

        swipeRefresh.setColorSchemeResources(R.color.stfx_navy, R.color.stfx_gold);
        swipeRefresh.setOnRefreshListener(() -> {
            currentNewsPage = 1;
            hasMorePages = true;
            DataRepository.getInstance().clearNewsItems();
            LiveSyncManager.getInstance().syncAll(requireContext(), success -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        renderNews();
                    });
                }
            });
        });

        // Trigger initial live sync if list is empty
        if (DataRepository.getInstance().getNewsItems().isEmpty()) {
            swipeRefresh.setRefreshing(true);
            tvNoNews.setVisibility(View.GONE);
            LiveSyncManager.getInstance().syncAll(requireContext(), success -> {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        swipeRefresh.setRefreshing(false);
                        renderNews();
                    });
                }
            });
        } else {
            renderNews();
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        DataRepository.getInstance().addListener(dataListener);
        renderNews();
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
                renderNews();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupPagination() {
        if (btnLoadMore != null) {
            btnLoadMore.setOnClickListener(v -> loadMoreNews());
        }

        if (scrollNews != null) {
            scrollNews.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > oldScrollY) {
                    View child = v.getChildAt(0);
                    if (child != null) {
                        int diff = child.getBottom() - (v.getHeight() + scrollY);
                        if (diff <= dpToPx(350)) {
                            if (currentQuery.isEmpty()) {
                                loadMoreNews();
                            }
                        }
                    }
                }
            });
        }
    }

    private void loadMoreNews() {
        if (isLoadingMore || !hasMorePages) return;
        isLoadingMore = true;
        if (layoutLoadingMore != null) layoutLoadingMore.setVisibility(View.VISIBLE);
        if (btnLoadMore != null) btnLoadMore.setVisibility(View.GONE);

        LiveSyncManager.getInstance().fetchNewsPage(requireContext(), currentNewsPage, moreItems -> {
            if (!isAdded()) return;
            isLoadingMore = false;
            if (layoutLoadingMore != null) layoutLoadingMore.setVisibility(View.GONE);

            if (moreItems != null && !moreItems.isEmpty()) {
                currentNewsPage++;
                DataRepository.getInstance().appendNewsItems(moreItems);
                renderNews();
                if (btnLoadMore != null && currentQuery.isEmpty()) {
                    btnLoadMore.setVisibility(View.VISIBLE);
                }
            } else {
                if (currentNewsPage >= 47) {
                    hasMorePages = false;
                    if (btnLoadMore != null) btnLoadMore.setVisibility(View.GONE);
                } else {
                    if (btnLoadMore != null && currentQuery.isEmpty()) {
                        btnLoadMore.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
    }

    private void renderNews() {
        if (!isAdded()) return;
        containerNews.removeAllViews();
        List<Models.NewsItem> all = DataRepository.getInstance().getNewsItems();
        List<Models.NewsItem> uniqueList = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();

        for (Models.NewsItem item : all) {
            String key = (item.url != null && !item.url.isEmpty()) ? item.url : item.title;
            if (seenKeys.contains(key)) {
                continue;
            }
            seenKeys.add(key);

            if (!currentQuery.isEmpty()) {
                boolean matchesTitle = item.title != null && item.title.toLowerCase().contains(currentQuery);
                boolean matchesSnippet = item.snippet != null && item.snippet.toLowerCase().contains(currentQuery);
                boolean matchesContent = item.content != null && item.content.toLowerCase().contains(currentQuery);
                if (!matchesTitle && !matchesSnippet && !matchesContent) {
                    continue;
                }
            }
            uniqueList.add(item);
        }

        boolean isFiltering = !currentQuery.isEmpty();
        Models.NewsItem featuredItem = null;

        if (!isFiltering && !uniqueList.isEmpty()) {
            // Find explicitly featured or use the first item
            for (Models.NewsItem item : uniqueList) {
                if (item.isFeatured) {
                    featuredItem = item;
                    break;
                }
            }
            if (featuredItem == null) {
                featuredItem = uniqueList.get(0);
            }

            final Models.NewsItem fItem = featuredItem;
            cardFeaturedNews.setVisibility(View.VISIBLE);
            tvFeaturedTitle.setText(fItem.title);
            tvFeaturedDesc.setVisibility(View.GONE);
            tvFeaturedTime.setText(fItem.date + " • " + fItem.readTime);
            
            ImageView ivFeatured = (ImageView) cardFeaturedNews.getTag();
            if (ivFeatured != null) {
                if (fItem.imageUrl != null && !fItem.imageUrl.isEmpty()) {
                    ivFeatured.setVisibility(View.VISIBLE);
                    Glide.with(this).load(fItem.imageUrl).into(ivFeatured);
                } else {
                    ivFeatured.setVisibility(View.GONE);
                }
            }
            
            cardFeaturedNews.setOnClickListener(v -> {
                WebViewerActivity.open(requireContext(), fItem.url, fItem.title);
            });
            View btnReadFeatured = cardFeaturedNews.findViewById(R.id.btn_read_featured);
            if (btnReadFeatured != null) {
                btnReadFeatured.setOnClickListener(v -> {
                    WebViewerActivity.open(requireContext(), fItem.url, fItem.title);
                });
            }
        } else {
            cardFeaturedNews.setVisibility(View.GONE);
        }

        if (uniqueList.isEmpty()) {
            if (currentQuery.isEmpty()) {
                tvNoNews.setText("Loading articles, please wait...");
            } else {
                tvNoNews.setText("No articles match \"" + currentQuery + "\"");
            }
            tvNoNews.setVisibility(View.VISIBLE);
            if (btnLoadMore != null) btnLoadMore.setVisibility(View.GONE);
            return;
        } else {
            tvNoNews.setVisibility(View.GONE);
        }

        if (btnLoadMore != null) {
            if (!currentQuery.isEmpty() || !hasMorePages) {
                btnLoadMore.setVisibility(View.GONE);
            } else if (!isLoadingMore) {
                btnLoadMore.setVisibility(View.VISIBLE);
            }
        }

        for (Models.NewsItem item : uniqueList) {
            // Skip showing the featured item twice in the default full list
            if (item == featuredItem && !isFiltering) {
                continue;
            }

            MaterialCardView card = new MaterialCardView(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dpToPx(14));
            card.setLayoutParams(lp);
            card.setRadius(dpToPx(14));
            card.setCardElevation(dpToPx(2));
            card.setCardBackgroundColor(getResources().getColor(R.color.card_bg, null));
            card.setStrokeColor(getResources().getColor(R.color.divider_color, null));
            card.setStrokeWidth(dpToPx(1));

            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);

            // Article Image (if available)
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                ImageView ivNews = new ImageView(requireContext());
                LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(160));
                ivNews.setLayoutParams(imgLp);
                ivNews.setScaleType(ImageView.ScaleType.CENTER_CROP);
                ivNews.setBackgroundColor(0xFF00143F);
                Glide.with(this)
                        .load(item.imageUrl)
                        .placeholder(R.drawable.bg_video_thumbnail)
                        .into(ivNews);
                content.addView(ivNews);
            }

            LinearLayout textContent = new LinearLayout(requireContext());
            textContent.setOrientation(LinearLayout.VERTICAL);
            textContent.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(14));

            // Meta row (Category + Date)
            LinearLayout metaRow = new LinearLayout(requireContext());
            metaRow.setOrientation(LinearLayout.HORIZONTAL);
            metaRow.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvCategory = new TextView(requireContext());
            tvCategory.setText(item.category.toUpperCase());
            tvCategory.setTextSize(10);
            tvCategory.setTypeface(null, android.graphics.Typeface.BOLD);
            tvCategory.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            tvCategory.setBackgroundResource(R.drawable.bg_badge_gold);
            tvCategory.setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2));
            metaRow.addView(tvCategory);

            TextView tvDate = new TextView(requireContext());
            tvDate.setText(item.date);
            tvDate.setTextSize(12);
            tvDate.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvDate.setPadding(dpToPx(8), 0, 0, 0);
            metaRow.addView(tvDate);

            textContent.addView(metaRow);

            // Title
            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(item.title);
            tvTitle.setTextSize(15);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvTitle.setPadding(0, dpToPx(6), 0, dpToPx(4));
            textContent.addView(tvTitle);



            // Action row: Byline + "Read Full Article →"
            LinearLayout actionRow = new LinearLayout(requireContext());
            actionRow.setOrientation(LinearLayout.HORIZONTAL);
            actionRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            actionRow.setPadding(0, dpToPx(10), 0, 0);

            TextView tvSource = new TextView(requireContext());
            tvSource.setText("By " + item.author);
            tvSource.setTextSize(11);
            tvSource.setTextColor(getResources().getColor(R.color.stfx_blue, null));
            LinearLayout.LayoutParams srcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvSource.setLayoutParams(srcLp);
            actionRow.addView(tvSource);

            TextView tvReadMore = new TextView(requireContext());
            tvReadMore.setText("Read Full Article →");
            tvReadMore.setTextSize(12);
            tvReadMore.setTypeface(null, android.graphics.Typeface.BOLD);
            tvReadMore.setTextColor(getResources().getColor(R.color.stfx_navy, null));
            actionRow.addView(tvReadMore);

            textContent.addView(actionRow);
            content.addView(textContent);
            card.addView(content);

            // Clicking the card or read full article opens the actual webpage directly!
            card.setOnClickListener(v -> {
                String articleUrl = (item.url != null && !item.url.isEmpty()) ? item.url : "https://www.stfx.ca/news";
                WebViewerActivity.open(requireContext(), articleUrl, item.title);
            });

            containerNews.addView(card);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
