package com.alan.tvlive;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
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
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.util.Random;

public class MainActivity extends Activity {
    // 去广告开关：拦截后视频播不出来就改成 false
    static final boolean BLOCK_ADS = true;
    // 调试提示：需要排查问题时改成 true
    static final boolean DEBUG = false;
    // 用 App 自己的音量条（false = 完全交给系统处理音量键）
    static final boolean CUSTOM_VOLUME = true;
    // 模拟点击播放后，锁定操作并等待的毫秒数（期间遥控器所有按键无效，之后才放大）
    static final int LOCK_MS = 15000;
    // 切到别的 App 后，在后台超过这个毫秒数就彻底退出
    static final long BG_EXIT_MS = 30000;
    // 启动加载页至少显示多久（毫秒），避免一闪而过
    static final long LOADING_MIN_MS = 1500;
    // 频道栏多久没操作就自动关闭
    static final long PANEL_HIDE_MS = 10000;
    // 音量条显示多久
    static final long VOL_HIDE_MS = 2000;

    // 菜单上的专属文字，想改话就改这里
    static final String MENU_BRAND = "TVB 直播";
    static final String MENU_TITLE = "May姐专属";
    static final String MENU_SUB = "天天开心　看得开心";

    // 主题色
    static final int COLOR_BG = 0xFF111111;
    static final int COLOR_ACCENT = 0xFFFFB300;
    // 选台菜单（在壁纸上）用半透明
    static final int COLOR_ROW = 0x66000000;
    static final int COLOR_BADGE = 0x29FFFFFF;
    // 频道栏（盖在视频上）用不透明，保证看得清
    static final int COLOR_PROW = 0xFF242424;
    static final int COLOR_PBADGE = 0xFF3A3A3A;

    // 壁纸颜色
    static final int COLOR_WALL = 0xFF2B1620;
    static final int COLOR_PINK = 0xFFF4A6B8;
    static final int COLOR_CORAL = 0xFFF28B82;
    static final int COLOR_GOLD = 0xFFF7C873;
    static final int COLOR_LEAF = 0xFF4F7F58;

    // 频道列表：名字和网址一一对应，要增减频道就改这两个数组
    static final String[] NAMES = {
        "TVB Plus",
        "TVB明珠台",
        "TVB翡翠台",
        "無綫新聞台",
        "無綫財經·體育·資訊台"
    };
    static final String[] URLS = {
        "https://live.yes2049.com/channels/j2/",
        "https://live.yes2049.com/channels/tvb-pearl/",
        "https://live.yes2049.com/channels/tvb-jade/",
        "https://live.yes2049.com/channels/tvb-news-channel/",
        "https://live.yes2049.com/channels/tvb-finance-information-channel/"
    };

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

    // 选台菜单的壁纸：酒红底色 + 四角的花和叶子 + 小金点，全部用代码画，不需要图片文件
    static class Wallpaper extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF petal = new RectF(-12, -44, 12, 0);
        final RectF leafRect = new RectF(-8, -36, 8, 0);

        Wallpaper(Context c) {
            super(c);
        }

        void flower(Canvas c, float x, float y, float sc, int color) {
            c.save();
            c.translate(x, y);
            c.scale(sc, sc);
            p.setColor(color);
            for (int i = 0; i < 5; i++) {
                c.save();
                c.rotate(72 * i);
                c.drawOval(petal, p);
                c.restore();
            }
            p.setColor(COLOR_ACCENT);
            c.drawCircle(0, 0, 9, p);
            c.restore();
        }

        void leaf(Canvas c, float x, float y, float rot, float sc) {
            c.save();
            c.translate(x, y);
            c.rotate(rot);
            c.scale(sc, sc);
            p.setColor(COLOR_LEAF);
            c.drawOval(leafRect, p);
            c.restore();
        }

        void dot(Canvas c, float x, float y, float r, int color) {
            p.setColor(color);
            c.drawCircle(x, y, r, p);
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            c.drawColor(COLOR_WALL);
            // 按 640x360 的画布设计，等比放大铺满屏幕
            float s = Math.max(w / 640f, h / 360f);
            c.save();
            c.translate((w - 640 * s) / 2f, (h - 360 * s) / 2f);
            c.scale(s, s);

            // 左上角
            leaf(c, 40, 70, -60, 1.6f);
            leaf(c, 40, 70, 20, 1.6f);
            leaf(c, 40, 70, 100, 1.4f);
            flower(c, 48, 60, 1.5f, COLOR_PINK);
            flower(c, 110, 30, 0.9f, COLOR_CORAL);
            flower(c, 18, 140, 0.8f, COLOR_GOLD);

            // 右下角
            leaf(c, 600, 310, 160, 1.6f);
            leaf(c, 600, 310, 240, 1.6f);
            leaf(c, 600, 310, -20, 1.4f);
            flower(c, 592, 300, 1.5f, COLOR_PINK);
            flower(c, 530, 332, 0.9f, COLOR_GOLD);
            flower(c, 624, 222, 0.8f, COLOR_CORAL);

            // 右上角
            leaf(c, 596, 48, 120, 1.1f);
            flower(c, 596, 48, 0.9f, COLOR_GOLD);

            // 左下角
            leaf(c, 46, 318, 50, 1.1f);
            flower(c, 46, 318, 0.9f, COLOR_PINK);

            // 小金点
            dot(c, 170, 60, 3f, COLOR_ACCENT);
            dot(c, 470, 40, 2.5f, COLOR_PINK);
            dot(c, 520, 110, 2f, COLOR_ACCENT);
            dot(c, 130, 290, 2.5f, COLOR_PINK);
            dot(c, 480, 320, 3f, COLOR_ACCENT);
            dot(c, 70, 220, 2f, COLOR_ACCENT);

            c.restore();
        }
    }

    // 音量条左边的小喇叭图标
    static class SpeakerIcon extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path body = new Path();
        Path w1 = new Path();
        Path w2 = new Path();

        SpeakerIcon(Context c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(1.8f);
            p.setColor(COLOR_ACCENT);
            body.moveTo(4f, 9.5f);
            body.rLineTo(0f, 5f);
            body.rLineTo(3.5f, 0f);
            body.lineTo(12f, 18.5f);
            body.rLineTo(0f, -13f);
            body.lineTo(7.5f, 9.5f);
            body.close();
            w1.moveTo(15.5f, 9f);
            w1.quadTo(18f, 12f, 15.5f, 15f);
            w2.moveTo(18f, 6.5f);
            w2.quadTo(23f, 12f, 18f, 17.5f);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = getWidth() / 24f;
            c.scale(s, s);
            c.drawPath(body, p);
            c.drawPath(w1, p);
            c.drawPath(w2, p);
        }
    }

    WebView web;
    FrameLayout root;
    View menu;
    View loading;
    View panel;
    View volBox;
    View volFill;
    View volRest;
    TextView volText;
    LinearLayout[] btns = new LinearLayout[NAMES.length];
    LinearLayout[] pRows = new LinearLayout[NAMES.length];
    View customView;
    WebChromeClient.CustomViewCallback customCb;
    AudioManager audio;
    Handler h = new Handler(Looper.getMainLooper());
    // 后台退出倒计时单独用一个 Handler，避免被 h.removeCallbacksAndMessages(null) 清掉
    Handler bgH = new Handler(Looper.getMainLooper());
    // 频道栏、音量条的自动隐藏也单独用一个 Handler
    Handler ph = new Handler(Looper.getMainLooper());
    Random rnd = new Random();
    long lastBack = 0;
    long lockUntil = 0;
    long stopTime = 0;
    int gen = 0;
    int pollGen = -1;
    int polls = 0;
    int pSel = 0;
    boolean tapped = false;
    boolean inPlayer = false;
    boolean cleaned = false;
    boolean panelOpen = false;
    int idx = 0;

    Runnable exitTask = new Runnable() {
        @Override
        public void run() { quitApp(); }
    };

    Runnable panelHide = new Runnable() {
        @Override
        public void run() { closePanel(); }
    };

    Runnable volHide = new Runnable() {
        @Override
        public void run() { volBox.setVisibility(View.GONE); }
    };

    // 清理完成（或超时）后，关闭加载页，进入选台菜单
    Runnable finishClean = new Runnable() {
        @Override
        public void run() {
            if (cleaned) return;
            cleaned = true;
            loading.setVisibility(View.GONE);
            showMenu();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        web.setVisibility(View.GONE);
        buildMenu();
        buildPanel();
        buildVolume();
        buildLoading();
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
                if (!inPlayer) return;
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

        // 先显示加载页并清理 cookies，清完再进选台菜单
        startClean();
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    LinearLayout.LayoutParams lp(int w, int hh, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh);
        p.setMargins(l, t, r, b);
        return p;
    }

    GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    // 菜单一行的外观：选中 = 琥珀底黑字，没选中 = 半透明深色底白字
    void paint(LinearLayout row, boolean focused) {
        row.setBackground(round(focused ? COLOR_ACCENT : COLOR_ROW, 14));
        TextView badge = (TextView) row.getChildAt(0);
        badge.setBackground(round(focused ? 0xFF000000 : COLOR_BADGE, 10));
        badge.setTextColor(COLOR_ACCENT);
        TextView name = (TextView) row.getChildAt(1);
        name.setTextColor(focused ? 0xFF000000 : 0xFFFFFFFF);
        TextView arrow = (TextView) row.getChildAt(2);
        arrow.setTextColor(0xFF000000);
        arrow.setVisibility(focused ? View.VISIBLE : View.INVISIBLE);
    }

    // 启动加载页：小标题 + 转圈 + 提示
    void buildLoading() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(COLOR_BG);

        TextView brand = new TextView(this);
        brand.setText(MENU_BRAND);
        brand.setTextColor(COLOR_ACCENT);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        brand.setLetterSpacing(0.15f);
        col.addView(brand, lp(-2, -2, 0, 0, 0, dp(24)));

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminateTintList(ColorStateList.valueOf(COLOR_ACCENT));
        col.addView(pb, lp(dp(64), dp(64), 0, 0, 0, dp(24)));

        TextView t = new TextView(this);
        t.setText("正在准备，请稍候…");
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        col.addView(t, lp(-2, -2, 0, 0, 0, dp(8)));

        TextView hint = new TextView(this);
        hint.setText("正在清理缓存，马上就好");
        hint.setTextColor(0xFF888888);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        col.addView(hint, lp(-2, -2, 0, 0, 0, 0));

        loading = col;
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));
    }

    // 频道列表（遥控器上下键选、OK 键进入），背后是专属壁纸
    void buildMenu() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(new Wallpaper(this), new FrameLayout.LayoutParams(-1, -1));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(40), dp(16), dp(40), dp(16));
        sv.addView(col, new FrameLayout.LayoutParams(-1, -2));

        TextView brand = new TextView(this);
        brand.setText(MENU_BRAND);
        brand.setTextColor(COLOR_PINK);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        brand.setLetterSpacing(0.15f);
        brand.setGravity(Gravity.CENTER);
        col.addView(brand, lp(-2, -2, 0, 0, 0, 0));

        TextView title = new TextView(this);
        title.setText(MENU_TITLE);
        title.setTextColor(COLOR_ACCENT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title, lp(-2, -2, 0, 0, 0, dp(2)));

        TextView sub = new TextView(this);
        sub.setText(MENU_SUB);
        sub.setTextColor(0xFFF4D5DC);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, lp(-2, -2, 0, 0, 0, dp(14)));

        for (int i = 0; i < NAMES.length; i++) {
            final int n = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), 0, dp(24), 0);
            row.setFocusable(true);
            row.setClickable(true);

            TextView badge = new TextView(this);
            badge.setText(String.valueOf(i + 1));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            row.addView(badge, lp(dp(40), dp(40), 0, 0, 0, 0));

            TextView name = new TextView(this);
            name.setText(NAMES[i]);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.setMargins(dp(20), 0, dp(12), 0);
            row.addView(name, np);

            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
            arrow.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(arrow, lp(-2, -2, 0, 0, 0, 0));

            paint(row, false);
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean f) {
                    paint((LinearLayout) v, f);
                }
            });
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openChannel(n);
                }
            });
            col.addView(row, lp(dp(640), dp(58), 0, dp(4), 0, dp(4)));
            btns[i] = row;
        }

        wrap.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        menu = wrap;
        root.addView(menu, new FrameLayout.LayoutParams(-1, -1));
    }

    // 播放时按 ← 弹出的频道栏（左侧，占屏幕宽度约 36%）
    void buildPanel() {
        int w = (int) (getResources().getDisplayMetrics().widthPixels * 0.36f);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xEE0E0E0E);
        col.setPadding(dp(24), dp(28), dp(24), dp(20));

        TextView brand = new TextView(this);
        brand.setText(MENU_BRAND);
        brand.setTextColor(COLOR_ACCENT);
        brand.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        brand.setLetterSpacing(0.15f);
        col.addView(brand, lp(-2, -2, 0, 0, 0, 0));

        TextView title = new TextView(this);
        title.setText("频道");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(title, lp(-2, -2, 0, dp(2), 0, dp(14)));

        for (int i = 0; i < NAMES.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), 0, dp(14), 0);

            TextView badge = new TextView(this);
            badge.setText(String.valueOf(i + 1));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            row.addView(badge, lp(dp(40), dp(40), 0, 0, 0, 0));

            TextView name = new TextView(this);
            name.setText(NAMES[i]);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1f);
            np.setMargins(dp(14), 0, dp(8), 0);
            row.addView(name, np);

            TextView tag = new TextView(this);
            tag.setText("播放中");
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tag.setTypeface(Typeface.DEFAULT_BOLD);
            tag.setPadding(dp(8), dp(2), dp(8), dp(2));
            row.addView(tag, lp(-2, -2, 0, 0, 0, 0));

            col.addView(row, lp(-1, dp(62), 0, dp(5), 0, dp(5)));
            pRows[i] = row;
        }

        View sp = new View(this);
        col.addView(sp, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView tip = new TextView(this);
        tip.setText("上下键选择　OK 切换　→ 关闭");
        tip.setTextColor(0xFF8A8A8A);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tip.setGravity(Gravity.CENTER);
        col.addView(tip, lp(-1, -2, 0, 0, 0, 0));

        panel = col;
        panel.setVisibility(View.GONE);
        root.addView(panel, new FrameLayout.LayoutParams(w, -1, Gravity.START));
        paintPanel();
    }

    // 频道栏一行的外观：选中 = 琥珀底黑字；正在播放的台带「播放中」标签
    void paintPanel() {
        for (int i = 0; i < pRows.length; i++) {
            LinearLayout row = pRows[i];
            boolean f = i == pSel;
            row.setBackground(round(f ? COLOR_ACCENT : COLOR_PROW, 14));
            TextView badge = (TextView) row.getChildAt(0);
            badge.setBackground(round(f ? 0xFF000000 : COLOR_PBADGE, 10));
            badge.setTextColor(COLOR_ACCENT);
            TextView name = (TextView) row.getChildAt(1);
            name.setTextColor(f ? 0xFF000000 : 0xFFFFFFFF);
            TextView tag = (TextView) row.getChildAt(2);
            tag.setVisibility(i == idx ? View.VISIBLE : View.INVISIBLE);
            tag.setBackground(round(f ? 0xFF000000 : COLOR_ACCENT, 20));
            tag.setTextColor(f ? COLOR_ACCENT : 0xFF000000);
        }
    }

    // 底部音量条：喇叭图标 + 「音量」+ 进度条 + 百分比
    void buildVolume() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(round(0xE6121212, 22));
        box.setPadding(dp(28), 0, dp(28), 0);

        box.addView(new SpeakerIcon(this), lp(dp(46), dp(46), 0, 0, 0, 0));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, -2, 1f);
        mp.setMargins(dp(22), 0, dp(18), 0);
        box.addView(mid, mp);

        TextView label = new TextView(this);
        label.setText("音量");
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        mid.addView(label, lp(-2, -2, 0, 0, 0, 0));

        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(round(0xFF3A3A3A, 8));
        mid.addView(track, lp(-1, dp(12), 0, dp(10), 0, 0));

        volFill = new View(this);
        volFill.setBackground(round(COLOR_ACCENT, 8));
        track.addView(volFill, new LinearLayout.LayoutParams(0, -1, 50f));
        volRest = new View(this);
        track.addView(volRest, new LinearLayout.LayoutParams(0, -1, 50f));

        volText = new TextView(this);
        volText.setText("0%");
        volText.setTextColor(COLOR_ACCENT);
        volText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        volText.setTypeface(Typeface.DEFAULT_BOLD);
        volText.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        volText.setMinWidth(dp(120));
        box.addView(volText, lp(-2, -2, 0, 0, 0, 0));

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(560), dp(104),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = dp(48);
        volBox = box;
        volBox.setVisibility(View.GONE);
        root.addView(volBox, fp);
    }

    // 调音量并显示音量条
    void adjustVolume(boolean up) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                up ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        showVolume();
    }

    void showVolume() {
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        int pct = max > 0 ? Math.round(cur * 100f / max) : 0;
        LinearLayout.LayoutParams a = (LinearLayout.LayoutParams) volFill.getLayoutParams();
        a.weight = pct;
        volFill.setLayoutParams(a);
        LinearLayout.LayoutParams c = (LinearLayout.LayoutParams) volRest.getLayoutParams();
        c.weight = 100 - pct;
        volRest.setLayoutParams(c);
        volText.setText(pct + "%");
        volBox.setVisibility(View.VISIBLE);
        ph.removeCallbacks(volHide);
        ph.postDelayed(volHide, VOL_HIDE_MS);
    }

    void openPanel() {
        if (!inPlayer || panelOpen) return;
        pSel = idx;
        paintPanel();
        panel.setVisibility(View.VISIBLE);
        panelOpen = true;
        schedulePanelHide();
    }

    void closePanel() {
        ph.removeCallbacks(panelHide);
        panelOpen = false;
        panel.setVisibility(View.GONE);
    }

    void schedulePanelHide() {
        ph.removeCallbacks(panelHide);
        ph.postDelayed(panelHide, PANEL_HIDE_MS);
    }

    // 频道栏打开时的按键：上下选择，OK 切换，→ 关闭
    void handlePanelKey(int k) {
        int n = URLS.length;
        if (k == KeyEvent.KEYCODE_DPAD_UP) {
            pSel = (pSel - 1 + n) % n;
            paintPanel();
            schedulePanelHide();
        } else if (k == KeyEvent.KEYCODE_DPAD_DOWN) {
            pSel = (pSel + 1) % n;
            paintPanel();
            schedulePanelHide();
        } else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            int sel = pSel;
            closePanel();
            if (sel != idx) openChannel(sel);
        } else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) {
            closePanel();
        } else {
            schedulePanelHide();
        }
    }

    // 启动时清理 cookies 和网页数据，期间显示加载页，清完再进菜单
    void startClean() {
        cleaned = false;
        menu.setVisibility(View.GONE);
        web.setVisibility(View.GONE);
        loading.setVisibility(View.VISIBLE);

        final long t0 = SystemClock.uptimeMillis();
        WebStorage.getInstance().deleteAllData();
        web.clearCache(true);
        web.clearHistory();
        CookieManager.getInstance().removeAllCookies(new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean ok) {
                CookieManager.getInstance().flush();
                long wait = LOADING_MIN_MS - (SystemClock.uptimeMillis() - t0);
                h.postDelayed(finishClean, wait > 0 ? wait : 0);
            }
        });
        // 保底：6 秒内没清完也照样进菜单，避免卡死在加载页
        h.postDelayed(finishClean, 6000);
    }

    void openChannel(int i) {
        idx = i;
        inPlayer = true;
        closePanel();
        h.removeCallbacksAndMessages(null);
        gen++;
        tapped = false;
        polls = 0;
        lockUntil = 0;
        pollGen = -1;
        menu.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        web.loadUrl(URLS[i]);
    }

    void showMenu() {
        inPlayer = false;
        closePanel();
        h.removeCallbacksAndMessages(null);
        gen++;
        tapped = false;
        lockUntil = 0;
        web.loadUrl("about:blank");
        web.setVisibility(View.GONE);
        menu.setVisibility(View.VISIBLE);
        btns[idx].requestFocus();
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

    // 彻底退出：关掉任务并结束进程，下次打开是全新启动
    void quitApp() {
        finishAndRemoveTask();
        Process.killProcess(Process.myPid());
    }

    // 切到别的 App：开始 30 秒倒计时
    @Override
    protected void onStop() {
        super.onStop();
        web.onPause();
        stopTime = SystemClock.elapsedRealtime();
        bgH.postDelayed(exitTask, BG_EXIT_MS);
    }

    // 回到本 App：取消倒计时；如果系统冻结了定时器，这里再判断一次
    @Override
    protected void onStart() {
        super.onStart();
        bgH.removeCallbacks(exitTask);
        if (stopTime > 0 && SystemClock.elapsedRealtime() - stopTime > BG_EXIT_MS) {
            quitApp();
            return;
        }
        stopTime = 0;
        web.onResume();
    }

    // 按键总入口：
    // 1. 音量键：不受锁定影响，用 App 自己的音量条（音量固定的设备交回系统）
    // 2. 锁定期间：其余按键全部不响应
    // 3. 频道栏打开时：上下/OK/→ 由频道栏处理，返回键走 onBackPressed
    // 4. 播放中按 ←：打开频道栏
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;

        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (CUSTOM_VOLUME && audio != null && !audio.isVolumeFixed()) {
                if (down) adjustVolume(k == KeyEvent.KEYCODE_VOLUME_UP);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (k == KeyEvent.KEYCODE_VOLUME_MUTE) return super.dispatchKeyEvent(e);

        if (System.currentTimeMillis() < lockUntil) return true;

        if (panelOpen && k != KeyEvent.KEYCODE_BACK) {
            if (down) handlePanelKey(k);
            return true;
        }
        if (inPlayer && k == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (down && e.getRepeatCount() == 0) openPanel();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (panelOpen) {
            closePanel();
            return;
        }
        if (customView != null) {
            hideCustom();
            return;
        }
        if (inPlayer) {
            showMenu();
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
        if (inPlayer) {
            if (k == KeyEvent.KEYCODE_CHANNEL_UP) {
                openChannel((idx + 1) % URLS.length);
                return true;
            }
            if (k == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                openChannel((idx - 1 + URLS.length) % URLS.length);
                return true;
            }
        }
        return super.onKeyDown(k, e);
    }
}
