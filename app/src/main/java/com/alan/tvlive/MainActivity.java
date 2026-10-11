package com.alan.tvlive;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {
    // 去广告开关：拦截后视频播不出来就改成 false
    static final boolean BLOCK_ADS = true;
    // 调试提示：调好后改成 false
    static final boolean DEBUG = true;

    static final String[] ALLOW_HOSTS = { "yes2049.com", "yeslivetv.com", "cloudflare" };
    static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "adservice.google", "2mdn.net", "adnxs.com",
        "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com",
        "pubmatic.com", "rubiconproject.com", "adsrvr.org", "moatads.com"
    };

    static final String JS =
        "(function(){" +
        "if(window.__tv)return;window.__tv=1;" +
        "var css=document.createElement('style');" +
        "css.textContent='ins.adsbygoogle,iframe[id^=google_ads],div[id^=google_ads],iframe[src*=doubleclick],iframe[src*=googlesyndication]{display:none!important}';" +
        "(document.head||document.documentElement).appendChild(css);" +
        "var n=0;" +
        "var t=setInterval(function(){" +
        "n++;if(n>90){clearInterval(t);return;}" +
        "var vs=document.querySelectorAll('video'),v=null,best=0;" +
        "for(var i=0;i<vs.length;i++){var r=vs[i].getBoundingClientRect();var a=r.width*r.height;if(vs[i].closest('.video-js'))a+=1e9;if(a>best){best=a;v=vs[i];}}" +
        "if(!v)return;" +
        "var box=v.closest('.video-js')||v.parentElement;" +
        "if(box&&!box.__big){box.__big=1;var s=box.style;" +
        "s.setProperty('position','fixed','important');" +
        "s.setProperty('left','0','important');" +
        "s.setProperty('top','0','important');" +
        "s.setProperty('width','100vw','important');" +
        "s.setProperty('height','100vh','important');" +
        "s.setProperty('padding','0','important');" +
        "s.setProperty('margin','0','important');" +
        "s.setProperty('z-index','2147483647','important');" +
        "s.setProperty('background','#000','important');" +
        "document.documentElement.style.overflow='hidden';" +
        "document.body.style.overflow='hidden';}" +
        "if(v.paused){" +
        "var b=document.querySelector('.vjs-big-play-button');if(b)b.click();" +
        "v.muted=false;var p=v.play();if(p&&p.catch)p.catch(function(){});}" +
        "},1000);" +
        "})();";

    WebView web;
    FrameLayout root;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    long lastBack = 0;
    int idx = 0;
    // 先放测试网址，之后换成你的频道页
    String[] urls = { "https://live.yes2049.com/" };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                String h = r.getUrl().getHost();
                if (h == null) return true;
                for (String a : ALLOW_HOSTS) {
                    if (h.contains(a)) return false;
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (BLOCK_ADS && isAd(r.getUrl().getHost(), r.getUrl().toString())) {
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(v, r);
            }

            @Override
            public void onPageFinished(final WebView v, String url) {
                v.evaluateJavascript(JS, null);
                if (DEBUG) {
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            v.evaluateJavascript(
                                "(function(){var f=document.querySelectorAll('iframe'),s='';" +
                                "for(var i=0;i<f.length;i++)s+=(f[i].src||'').slice(0,40)+' | ';" +
                                "return 'video:'+document.querySelectorAll('video').length+' iframe:'+f.length+' '+s})()",
                                new android.webkit.ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String r) {
                                        Toast.makeText(MainActivity.this, r, Toast.LENGTH_LONG).show();
                                    }
                                });
                        }
                    }, 8000);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = v;
                customCb = cb;
                root.addView(v, new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                hideCustom();
            }
        });

        web.loadUrl(urls[idx]);
    }

    boolean isAd(String host, String url) {
        if (host != null) {
            for (String d : AD_HOSTS) {
                if (host.contains(d)) return true;
            }
        }
        return url.contains("/pagead/");
    }

    void hideCustom() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customCb != null) customCb.onCustomViewHidden();
        customCb = null;
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustom();
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2000) {
            super.onBackPressed();
        } else {
            lastBack = now;
            Toast.makeText(this, "再按一次返回键退出", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onKeyDown(int k, KeyEvent e) {
        if (urls.length > 1) {
            if (k == KeyEvent.KEYCODE_CHANNEL_UP) {
                idx = (idx + 1) % urls.length;
                web.loadUrl(urls[idx]);
                return true;
            }
            if (k == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                idx = (idx - 1 + urls.length) % urls.length;
                web.loadUrl(urls[idx]);
                return true;
            }
        }
        return super.onKeyDown(k, e);
    }
}
