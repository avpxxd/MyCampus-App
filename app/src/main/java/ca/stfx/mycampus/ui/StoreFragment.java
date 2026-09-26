package ca.stfx.mycampus.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import ca.stfx.mycampus.R;

public class StoreFragment extends Fragment {

    private WebView storeWebView;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefresh;
    private static final String STORE_URL = "https://shop.stfx.ca/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_store, container, false);

        storeWebView = view.findViewById(R.id.store_web_view);
        progressBar = view.findViewById(R.id.store_progress_bar);
        swipeRefresh = view.findViewById(R.id.swipe_refresh_store);

        setupSwipeRefresh();
        setupWebView();

        return view;
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeResources(R.color.stfx_navy, R.color.stfx_gold);
        swipeRefresh.setOnRefreshListener(() -> {
            if (storeWebView != null) {
                storeWebView.reload();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = storeWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        storeWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    if (newProgress < 100) {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
            }
        });

        storeWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) progressBar.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if ("tel".equalsIgnoreCase(scheme) || "mailto".equalsIgnoreCase(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                        return true;
                    } catch (Exception ignored) {}
                }

                // Keep store and checkout domains inside WebView
                String host = uri.getHost();
                if (host != null && (host.contains("shop.stfx.ca") || host.contains("bookware3000.ca") || host.contains("moneris.com") || host.contains("paypal.com"))) {
                    return false;
                }

                return false;
            }
        });

        storeWebView.loadUrl(STORE_URL);
    }

    public boolean canGoBack() {
        return storeWebView != null && storeWebView.canGoBack();
    }

    public void goBack() {
        if (storeWebView != null && storeWebView.canGoBack()) {
            storeWebView.goBack();
        }
    }
}
