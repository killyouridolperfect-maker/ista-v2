package app.clipcho;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * The floating button and the history panel drawn over other apps.
 * Tapping a clip pastes it straight into the text box that had focus.
 */
final class QuickPanel {

    static final String PREFS = "clipcho";
    static final String KEY_BUBBLE = "bubble";
    private static final String KEY_X = "bubble_x";
    private static final String KEY_Y = "bubble_y";
    private static final int PANEL_LIMIT = 150;

    private final AccessibilityService svc;
    private final WindowManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View bubble;
    private WindowManager.LayoutParams bubbleLp;
    private View panel;
    private LinearLayout listBox;
    private TextView chipAll, chipPin;
    private AccessibilityNodeInfo target;
    private boolean pinOnly = false;

    private int cSheet, cCard, cText, cMuted, cAccent, cAccentFg, cLine, cPin;

    QuickPanel(AccessibilityService service) {
        svc = service;
        wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean bubbleEnabled(Context c) {
        return prefs(c).getBoolean(KEY_BUBBLE, true);
    }

    void syncBubble() {
        if (bubbleEnabled(svc)) showBubble();
        else hideBubble();
    }

    void destroy() {
        hidePanel();
        hideBubble();
    }

    // ------------------------------------------------------------ helpers

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                svc.getResources().getDisplayMetrics()));
    }

    @SuppressWarnings("deprecation")
    private Point screen() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect r = wm.getCurrentWindowMetrics().getBounds();
            return new Point(r.width(), r.height());
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new Point(dm.widthPixels, dm.heightPixels);
    }

    private void loadColors() {
        boolean dark = (svc.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        cSheet = Color.parseColor(dark ? "#0F141B" : "#EEF1F5");
        cCard = Color.parseColor(dark ? "#18202A" : "#FFFFFF");
        cText = Color.parseColor(dark ? "#E8EDF3" : "#18202B");
        cMuted = Color.parseColor(dark ? "#97A3B3" : "#5D6878");
        cAccent = Color.parseColor(dark ? "#5B97F5" : "#1D5FD1");
        cAccentFg = Color.parseColor(dark ? "#0B1220" : "#FFFFFF");
        cLine = Color.parseColor(dark ? "#2B3644" : "#D4DBE4");
        cPin = Color.parseColor(dark ? "#F2B34A" : "#C77700");
    }

    private GradientDrawable box(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView label(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(svc);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    // ------------------------------------------------------------ bubble

    void showBubble() {
        if (bubble != null) return;
        loadColors();
        final int size = dp(46);
        ImageView b = new ImageView(svc);
        b.setImageResource(R.drawable.ic_stat_clip);
        b.setColorFilter(Color.WHITE);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#1D5FD1"));
        bg.setStroke(dp(2), Color.WHITE);
        b.setBackground(bg);
        b.setAlpha(0.88f);
        b.setElevation(dp(6));
        b.setContentDescription("クリップ帳を開く");

        Point s = screen();
        bubbleLp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences p = prefs(svc);
        bubbleLp.x = clamp(p.getInt(KEY_X, s.x - size - dp(4)), 0, s.x - size);
        bubbleLp.y = clamp(p.getInt(KEY_Y, (int) (s.y * 0.42f)), dp(24), s.y - size - dp(24));

        final int slop = ViewConfiguration.get(svc).getScaledTouchSlop();
        b.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY;
            int startX, startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = bubbleLp.x;
                        startY = bubbleLp.y;
                        moved = false;
                        v.setAlpha(1f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!moved && Math.hypot(dx, dy) > slop) moved = true;
                        if (moved) {
                            bubbleLp.x = startX + Math.round(dx);
                            bubbleLp.y = startY + Math.round(dy);
                            safeUpdate(v, bubbleLp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(0.88f);
                        if (!moved) {
                            if (e.getActionMasked() == MotionEvent.ACTION_UP) togglePanel();
                            return true;
                        }
                        Point sc = screen();
                        int sz = v.getWidth();
                        bubbleLp.x = (bubbleLp.x + sz / 2 < sc.x / 2) ? dp(4) : sc.x - sz - dp(4);
                        bubbleLp.y = clamp(bubbleLp.y, dp(24), sc.y - sz - dp(24));
                        safeUpdate(v, bubbleLp);
                        prefs(svc).edit().putInt(KEY_X, bubbleLp.x).putInt(KEY_Y, bubbleLp.y).apply();
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            wm.addView(b, bubbleLp);
            bubble = b;
        } catch (Exception ignored) {
        }
    }

    void hideBubble() {
        if (bubble == null) return;
        try {
            wm.removeView(bubble);
        } catch (Exception ignored) {
        }
        bubble = null;
    }

    private void safeUpdate(View v, WindowManager.LayoutParams lp) {
        try {
            wm.updateViewLayout(v, lp);
        } catch (Exception ignored) {
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------ panel

    void togglePanel() {
        if (panel != null) hidePanel();
        else showPanel();
    }

    void showPanel() {
        if (panel != null) return;
        loadColors();
        target = findTarget();

        FrameLayout root = new FrameLayout(svc);
        root.setBackgroundColor(Color.argb(110, 0, 0, 0));
        root.setOnClickListener(v -> hidePanel());

        LinearLayout sheet = new LinearLayout(svc);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true); // taps inside the sheet do not close it
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(cSheet);
        float r = dp(20);
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sheetBg);
        sheet.setPadding(dp(16), dp(14), dp(16), dp(16));

        // Header
        LinearLayout head = new LinearLayout(svc);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(svc);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(label("クリップ帳", 18, cText, true));
        titles.addView(label(target != null ? "タップすると、入力中の欄にそのまま貼り付けます"
                : "タップでコピー（入力欄を選んでから開くと直接貼り付け）", 12, cMuted, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = label("✕", 20, cMuted, true);
        close.setPadding(dp(14), dp(6), dp(6), dp(6));
        close.setContentDescription("閉じる");
        close.setOnClickListener(v -> hidePanel());
        head.addView(close);
        sheet.addView(head);

        // Filter chips
        LinearLayout chips = new LinearLayout(svc);
        chipAll = label("すべて", 14, cText, true);
        chipPin = label("★ ピン", 14, cText, true);
        for (TextView c : new TextView[]{chipAll, chipPin}) {
            c.setPadding(dp(14), dp(7), dp(14), dp(7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            chips.addView(c, lp);
        }
        chipAll.setOnClickListener(v -> { pinOnly = false; fillList(); });
        chipPin.setOnClickListener(v -> { pinOnly = true; fillList(); });
        LinearLayout.LayoutParams chipsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsLp.topMargin = dp(10);
        chipsLp.bottomMargin = dp(8);
        sheet.addView(chips, chipsLp);

        // List
        ScrollView scroll = new ScrollView(svc);
        listBox = new LinearLayout(svc);
        listBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(listBox, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Point s = screen();
        FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(s.y * 0.62f), Gravity.BOTTOM);
        root.addView(sheet, sheetLp);

        fillList();

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(root, lp);
            panel = root;
        } catch (Exception e) {
            ClipUtil.toast(svc, "履歴を表示できませんでした");
        }
    }

    void hidePanel() {
        if (panel == null) return;
        try {
            wm.removeView(panel);
        } catch (Exception ignored) {
        }
        panel = null;
        listBox = null;
    }

    private void fillList() {
        if (listBox == null) return;
        chipAll.setBackground(box(pinOnly ? cCard : cText, pinOnly ? cLine : 0, 999));
        chipAll.setTextColor(pinOnly ? cMuted : cSheet);
        chipPin.setBackground(box(pinOnly ? cText : cCard, pinOnly ? 0 : cLine, 999));
        chipPin.setTextColor(pinOnly ? cSheet : cMuted);

        listBox.removeAllViews();
        List<ClipDb.Clip> clips = ClipDb.get(svc).list(null, pinOnly);
        if (clips.isEmpty()) {
            TextView empty = label(pinOnly ? "ピン留めはまだありません。アプリの一覧で ☆ を押すと、ここに出ます。"
                    : "まだ履歴がありません。他のアプリで「コピー」すると、ここに貯まります。", 14, cMuted, false);
            empty.setPadding(dp(4), dp(16), dp(4), dp(16));
            listBox.addView(empty);
            return;
        }
        int n = Math.min(clips.size(), PANEL_LIMIT);
        for (int i = 0; i < n; i++) {
            final ClipDb.Clip c = clips.get(i);
            TextView row = label((c.pin ? "★ " : "") + c.text, 15, cText, false);
            row.setMaxLines(3);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(box(cCard, c.pin ? cPin : cLine, 12));
            row.setOnClickListener(v -> choose(c.text));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            listBox.addView(row, lp);
        }
    }

    // ------------------------------------------------------------ paste

    private AccessibilityNodeInfo findTarget() {
        try {
            AccessibilityNodeInfo root = svc.getRootInActiveWindow();
            if (root == null) return null;
            AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (f != null && f.isEditable()) return f;
        } catch (Exception ignored) {
        }
        return null;
    }

    private void choose(String text) {
        final AccessibilityNodeInfo t = target;
        boolean copied = ClipUtil.copy(svc, text);
        hidePanel();
        if (!copied) {
            ClipUtil.toast(svc, "コピーできませんでした");
            return;
        }
        if (t == null) {
            ClipUtil.toast(svc, "コピーしました。貼り付けたい所を長押し →「貼り付け」");
            return;
        }
        // Let the panel close and the clipboard settle, then paste into the original box.
        handler.postDelayed(() -> {
            boolean pasted = false;
            try {
                t.refresh();
                pasted = t.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            } catch (Exception ignored) {
            }
            ClipUtil.toast(svc, pasted ? "貼り付けました" : "コピーしました。貼り付けたい所を長押し →「貼り付け」");
        }, 150);
    }
}
