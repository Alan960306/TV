package com.alan.tvlive;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.util.Random;

public class MainActivity extends Activity {
    // 去广告开关：拦截后视频播不出来就改成 false
    static final boolean BLOCK_ADS = true;
    // 调试提示：调好后改成 false
    static final boolean DEBUG = true;
    // 模拟点击播放后，锁定操作并等待的毫秒数（期间遥控器所有按键无效，之后才放大）
    static final int LOCK_MS = 15000;

    static final String[] ALLOW_HOSTS = { "yes2049.com", "yeslivetv.com", "cloudflare" };
    static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "googletagservices.com", "adservice.google", "2mdn.net", "adnxs.com",
        "amazon-adsystem.com", "taboola.com", "outbrain.com", "criteo.com",
        "pubmatic.com", "rubiconproject.com", "adsrvr.org", "moatads.com"
    };

    // 隐藏广告位
    static final String JS_INIT =
        "(function(){" +
        "if(window.__tv)return;window.__tv=1;" +
        "var css=document.createElement('style');" +
        "css.textContent='ins.adsbygoogle,iframe[id^=google_ads],div[id^=google_ads],iframe[src*=doubleclick],iframe[src*=googlesyndication]{display:none!important}';" +
        "(document.head||document.documentElement).appendChild(css);" +
        "})();";

    // 只找播放器位置，不点击。返回 "x,y,页面宽度" / "none" / "blocked"
    static final String JS_FIND =
        "(function(){" +
        "var vs=document.querySelectorAll('video'),v=null,best=0;" +
        "for(var i=0;i<vs.length;i++){var r0=vs[i].getBoundingClientRect();var a=r0.width*r0.height;if(vs[i].closest('.video-js'))a+=1e9;if(a>best){best=a;v=vs[i];}}" +
        "if(!v)return 'none';" +
        "var box=v.closest('.video-js')||v;" +
        "box.scrollIntoView({block:'center'});" +
        "var r=box.getBoundingClientRect();" +
        "if(r.width<100||r.height<80)return 'none';" +
        "var cx=r.left+r.width/2,cy=r.top+r.height/2;" +
        "if(cx<0||cy<0||cx>innerWidth||cy>innerHeight)return 'none';" +
        "var el=document.elementFromPoint(cx,cy);" +
        "if(!el||!box.contains(el))return 'blocked';" +
        "return cx+','+cy+','+innerWidth;" +
        "})()";

    // 只放大，不点播放
    static final String JS_ENLARGE =
        "(function(){" +
        "var vs=document.querySelectorAll('video'),v=null,best=0;" +
        "for(var i=0;i<vs.length;i++){var r0=vs[i].getBoundingClientRect();var a=r0.width*r0.height;if(vs[i].closest('.video-js'))a+=1e9;if(a>best){best=a;v=vs[i];}}" +
        "if(!v)return;" +
        "var box=v.closest('.video-js')||v.parentElement;if(!box)return;" +
        "var s=box.style;" +
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
        "document.body.style.overflow='hidden';" +
        "})();";

    WebView web;
    FrameLayout root;
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    Handler h = new Handler(Looper.getMainLooper());
    Random rnd = new Random();
    long lastBack = 0;
    long lockUntil = 0;
    int gen = 0;
    int pollGen = -1;
    int polls = 0;
    boolean tapped = false;
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
                String host = r.getUrl().getHost();
                if (host == null) return true;
                for (String a : ALLOW_HOSTS) {
                    if (host.contains(a)) return false;
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
            public void onPageStarted(WebView v, String url, Bitmap icon) {
                // 锁定期间页面的任何跳转都不重新开始，避免重复点击
                if (System.currentTimeMillis() < lockUntil) return;
                gen++;
                tapped = false;
                polls = 0;
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript(JS_INIT, null);
                if (!tapped && System.currentTimeMillis() >= lockUntil && pollGen != gen) {
                    pollGen = gen;
                    poll(gen);
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

    // 每秒找一次播放器，找到并且中心没被广告盖住，就准备模拟点击
    void poll(final int g) {
        if (g != gen || tapped) return;
        if (++polls > 150) return;
        web.evaluateJavascript(JS_FIND, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (g != gen || tapped) return;
                String r = value == null ? "" : value.replace("\"", "");
                if (r.matches("[-0-9.]+,[-0-9.]+,[0-9.]+")) {
                    String[] p = r.split(",");
                    float sc = web.getWidth() / Float.parseFloat(p[2]);
                    final float x = Float.parseFloat(p[0]) * sc;
                    final float y = Float.parseFloat(p[1]) * sc;
                    tapped = true;
                    // 从现在起锁定：600 毫秒后点击，再等 LOCK_MS
                    lockUntil = System.currentTimeMillis() + 600 + LOCK_MS;
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() { humanTap(x, y, g); }
                    }, 600);
                } else {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() { poll(g); }
                    }, 1000);
                }
            }
        });
    }

    // 发出真实的触摸事件：按下，停一会，抬起
    void humanTap(float x, float y, final int g) {
        if (g != gen) return;
        final float tx = x + (rnd.nextFloat() - 0.5f) * 8f;
        final float ty = y + (rnd.nextFloat() - 0.5f) * 8f;
        final long down = SystemClock.uptimeMillis();
        MotionEvent d = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, tx, ty, 0);
        d.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(d);
        d.recycle();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                long t = SystemClock.uptimeMillis();
                MotionEvent u = MotionEvent.obtain(down, t, MotionEvent.ACTION_UP, tx, ty, 0);
                u.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                web.dispatchTouchEvent(u);
                u.recycle();
            }
        }, 80 + rnd.nextInt(60));
        Toast.makeText(this, "正在加载，请稍候…", Toast.LENGTH_LONG).show();
        if (DEBUG) {
            Toast.makeText(this, "已模拟点击 " + (int) tx + "," + (int) ty, Toast.LENGTH_SHORT).show();
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (g == gen) web.evaluateJavascript(JS_ENLARGE, null);
            }
        }, LOCK_MS);
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

    // 锁定期间，遥控器所有按键都不响应
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (System.currentTimeMillis() < lockUntil) return true;
        return super.dispatchKeyEvent(e);
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
