package app.clipcho;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private ClipDb db;
    private final List<ClipDb.Clip> items = new ArrayList<>();
    private boolean pinOnly = false;

    private EditText search;
    private TextView statusPill, filterAll, filterPin, countView, emptyView;
    private LinearLayout setupCard;
    private ListView list;
    private ClipAdapter adapter;

    // palette
    private int cBg, cCard, cText, cMuted, cAccent, cAccentFg, cLine, cPin, cOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = ClipDb.get(this);
        initColors();
        buildUi();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        refresh();
    }

    /** Opening the app also saves whatever is on the clipboard right now. */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) return;
        String t = ClipUtil.readClipboard(this);
        if (db.add(t, true) == ClipDb.RESULT_NEW) {
            ClipUtil.toast(this, "コピー中の文字を保存しました");
            refresh();
        }
    }

    // ---------------------------------------------------------------- UI

    private void initColors() {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            cBg = Color.parseColor("#0F141B");
            cCard = Color.parseColor("#18202A");
            cText = Color.parseColor("#E8EDF3");
            cMuted = Color.parseColor("#97A3B3");
            cAccent = Color.parseColor("#5B97F5");
            cAccentFg = Color.parseColor("#0B1220");
            cLine = Color.parseColor("#2B3644");
            cPin = Color.parseColor("#F2B34A");
            cOk = Color.parseColor("#4CC98A");
        } else {
            cBg = Color.parseColor("#EEF1F5");
            cCard = Color.parseColor("#FFFFFF");
            cText = Color.parseColor("#18202B");
            cMuted = Color.parseColor("#5D6878");
            cAccent = Color.parseColor("#1D5FD1");
            cAccentFg = Color.parseColor("#FFFFFF");
            cLine = Color.parseColor("#D4DBE4");
            cPin = Color.parseColor("#C77700");
            cOk = Color.parseColor("#13804A");
        }
        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);
        if (!dark) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable box(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView button(String s, boolean primary) {
        TextView b = text(s, 15, primary ? cAccentFg : cText, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setBackground(box(primary ? cAccent : cCard, primary ? 0 : cLine, 12));
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(12), dp(16), 0);

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("クリップ帳", 22, cText, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusPill = text("", 12, cMuted, true);
        statusPill.setPadding(dp(10), dp(4), dp(10), dp(4));
        titleRow.addView(statusPill);
        root.addView(titleRow);

        // Setup card (shown while auto-save is off)
        setupCard = new LinearLayout(this);
        setupCard.setOrientation(LinearLayout.VERTICAL);
        setupCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        setupCard.setBackground(box(cCard, cAccent, 14));
        TextView sTitle = text("自動保存をオンにしましょう", 16, cText, true);
        setupCard.addView(sTitle);
        TextView sBody = text(
                "① 下の「ユーザー補助を開く」→「インストール済みアプリ」（または「ダウンロードしたアプリ」）→「クリップ帳 自動保存」をオン\n\n"
                        + "② スイッチが灰色で押せないときは「アプリ情報を開く」→ 右上の︙ →「制限付き設定を許可」を押してから、①をもう一度",
                14, cText, false);
        sBody.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams lpBody = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpBody.topMargin = dp(8);
        setupCard.addView(sBody, lpBody);
        LinearLayout sBtns = new LinearLayout(this);
        sBtns.setOrientation(LinearLayout.VERTICAL);
        TextView openA11y = button("ユーザー補助を開く", true);
        openA11y.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        TextView openInfo = button("アプリ情報を開く", false);
        openInfo.setOnClickListener(v -> openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        LinearLayout.LayoutParams lpB1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpB1.topMargin = dp(12);
        sBtns.addView(openA11y, lpB1);
        LinearLayout.LayoutParams lpB2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpB2.topMargin = dp(8);
        sBtns.addView(openInfo, lpB2);
        setupCard.addView(sBtns);
        LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCard.topMargin = dp(12);
        root.addView(setupCard, lpCard);

        // Search + paste
        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setHint("履歴を検索");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTextColor(cText);
        search.setHintTextColor(cMuted);
        search.setPadding(dp(12), dp(10), dp(12), dp(10));
        search.setBackground(box(cCard, cLine, 12));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refresh(); }
        });
        searchRow.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView paste = button("＋ 貼り付け", true);
        paste.setOnClickListener(v -> pasteNow());
        LinearLayout.LayoutParams lpPaste = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPaste.leftMargin = dp(8);
        searchRow.addView(paste, lpPaste);
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSearch.topMargin = dp(12);
        root.addView(searchRow, lpSearch);

        // Filters
        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);
        filterAll = text("すべて", 14, cText, true);
        filterPin = text("★ ピン", 14, cText, true);
        for (TextView f : new TextView[]{filterAll, filterPin}) {
            f.setPadding(dp(14), dp(7), dp(14), dp(7));
            f.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            filterRow.addView(f, lp);
        }
        filterAll.setOnClickListener(v -> { pinOnly = false; refresh(); });
        filterPin.setOnClickListener(v -> { pinOnly = true; refresh(); });
        countView = text("", 12, cMuted, false);
        countView.setGravity(Gravity.END);
        filterRow.addView(countView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lpFilter = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpFilter.topMargin = dp(10);
        lpFilter.bottomMargin = dp(4);
        root.addView(filterRow, lpFilter);

        // Empty message + list
        emptyView = text("", 14, cMuted, false);
        emptyView.setLineSpacing(0, 1.25f);
        emptyView.setPadding(dp(4), dp(24), dp(4), dp(24));
        root.addView(emptyView);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(24));
        list.setSelector(new GradientDrawable());
        adapter = new ClipAdapter();
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    private void openSettings(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            ClipUtil.toast(this, "設定画面を開けませんでした。設定アプリから開いてください");
        }
    }

    private void updateStatus() {
        boolean on = ClipUtil.isAutoSaveOn(this);
        statusPill.setText(on ? "● 自動保存 ON" : "● 自動保存 OFF");
        statusPill.setTextColor(on ? cOk : cPin);
        statusPill.setBackground(box(cCard, cLine, 999));
        setupCard.setVisibility(on ? View.GONE : View.VISIBLE);
    }

    private void refresh() {
        String q = search == null ? "" : search.getText().toString();
        items.clear();
        items.addAll(db.list(q, pinOnly));
        int total = db.count();
        countView.setText(items.size() == total ? total + "件" : items.size() + " / " + total + "件");

        filterAll.setBackground(box(pinOnly ? cCard : cText, pinOnly ? cLine : 0, 999));
        filterAll.setTextColor(pinOnly ? cMuted : cBg);
        filterPin.setBackground(box(pinOnly ? cText : cCard, pinOnly ? 0 : cLine, 999));
        filterPin.setTextColor(pinOnly ? cBg : cMuted);

        if (items.isEmpty()) {
            if (total == 0) {
                emptyView.setText("まだ履歴がありません。\n\n自動保存をオンにすると、他のアプリで「コピー」した文字がここに貯まります。"
                        + "\n\n文字を長押しして選んだときのメニューにある「クリップ帳に保存」や、「共有」→「クリップ帳に保存」でも保存できます。");
            } else if (pinOnly && q.trim().isEmpty()) {
                emptyView.setText("ピン留めはまだありません。履歴の ☆ を押すと、ここに集まります。");
            } else {
                emptyView.setText("「" + q.trim() + "」を含む履歴はありません。");
            }
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    // ---------------------------------------------------------------- actions

    private void pasteNow() {
        String t = ClipUtil.readClipboard(this);
        int r = db.add(t, false);
        if (r == ClipDb.RESULT_NEW) ClipUtil.toast(this, "保存しました");
        else if (r == ClipDb.RESULT_DUP) ClipUtil.toast(this, "保存済みです（一番上に移動しました）");
        else ClipUtil.toast(this, "コピー中の文字がありません");
        refresh();
    }

    private void copyClip(ClipDb.Clip c) {
        if (ClipUtil.copy(this, c.text)) ClipUtil.toast(this, "コピーしました");
        else ClipUtil.toast(this, "コピーできませんでした");
    }

    private void editClip(ClipDb.Clip c) {
        EditText et = new EditText(this);
        et.setText(c.text);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(4);
        et.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("編集")
                .setView(wrap)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("保存", (d, w) -> {
                    String v = ClipUtil.rtrim(et.getText().toString());
                    if (v == null || v.trim().isEmpty()) {
                        ClipUtil.toast(this, "空にはできません。消す場合は「削除」を押してください");
                        return;
                    }
                    db.updateText(c.id, v);
                    ClipUtil.toast(this, "変更を保存しました");
                    refresh();
                })
                .show();
    }

    private void deleteClip(ClipDb.Clip c) {
        String preview = c.text.length() > 60 ? c.text.substring(0, 60) + "…" : c.text;
        new AlertDialog.Builder(this)
                .setTitle("この履歴を削除しますか？")
                .setMessage(preview)
                .setNegativeButton("キャンセル", null)
                .setPositiveButton("削除", (d, w) -> {
                    db.delete(c.id);
                    ClipUtil.toast(this, "削除しました");
                    refresh();
                })
                .show();
    }

    private void showFull(ClipDb.Clip c) {
        new AlertDialog.Builder(this)
                .setMessage(c.text)
                .setNegativeButton("閉じる", null)
                .setPositiveButton("コピー", (d, w) -> copyClip(c))
                .show();
    }

    // ---------------------------------------------------------------- list

    private static final SimpleDateFormat HM = new SimpleDateFormat("HH:mm", Locale.JAPAN);
    private static final SimpleDateFormat MD = new SimpleDateFormat("M/d HH:mm", Locale.JAPAN);
    private static final SimpleDateFormat YMD = new SimpleDateFormat("yyyy/M/d HH:mm", Locale.JAPAN);

    private String when(long ts) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(ts);
        Date d = new Date(ts);
        if (now.get(Calendar.YEAR) == t.get(Calendar.YEAR)) {
            int diff = now.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR);
            if (diff == 0) return "今日 " + HM.format(d);
            if (diff == 1) return "昨日 " + HM.format(d);
            return MD.format(d);
        }
        return YMD.format(d);
    }

    private static class Holder {
        TextView body, meta, pin, edit, del;
    }

    private class ClipAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int p) { return items.get(p); }
        @Override public long getItemId(int p) { return items.get(p).id; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Holder h;
            View row = convertView;
            if (row == null) {
                LinearLayout outer = new LinearLayout(MainActivity.this);
                outer.setPadding(0, dp(4), 0, dp(4));

                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(14), dp(12), dp(6), dp(2));
                outer.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                h = new Holder();
                h.body = text("", 16, cText, false);
                h.body.setMaxLines(4);
                h.body.setEllipsize(TextUtils.TruncateAt.END);
                h.body.setPadding(0, 0, dp(8), dp(4));
                card.addView(h.body);

                LinearLayout meta = new LinearLayout(MainActivity.this);
                meta.setGravity(Gravity.CENTER_VERTICAL);
                h.meta = text("", 12, cMuted, false);
                meta.addView(h.meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                h.pin = action("☆");
                h.edit = action("編集");
                h.del = action("削除");
                meta.addView(h.pin);
                meta.addView(h.edit);
                meta.addView(h.del);
                card.addView(meta);

                outer.setTag(h);
                outer.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row = outer;
            } else {
                h = (Holder) row.getTag();
            }

            ClipDb.Clip c = items.get(position);
            View card = ((ViewGroup) row).getChildAt(0);
            card.setBackground(box(cCard, c.pin ? cPin : cLine, 14));
            h.body.setText(c.text);
            h.meta.setText(when(c.ts) + " · " + c.text.length() + "字");
            h.pin.setText(c.pin ? "★" : "☆");
            h.pin.setTextColor(c.pin ? cPin : cMuted);

            card.setOnClickListener(v -> copyClip(c));
            card.setOnLongClickListener(v -> { showFull(c); return true; });
            h.pin.setOnClickListener(v -> { db.setPin(c.id, !c.pin); refresh(); });
            h.edit.setOnClickListener(v -> editClip(c));
            h.del.setOnClickListener(v -> deleteClip(c));
            return row;
        }
    }

    private TextView action(String s) {
        TextView t = text(s, 14, cMuted, true);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44));
        t.setPadding(dp(8), dp(10), dp(8), dp(10));
        t.setClickable(true);
        return t;
    }
}
