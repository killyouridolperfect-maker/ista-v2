package app.clipcho;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Watches for "copy" actions in other apps (a tap on a "コピー" button, or a
 * "コピーしました" message) and then briefly opens {@link ClipReaderActivity} to save the clip.
 * Nothing on screen is stored: only the clipboard text after a copy.
 */
public class ClipAccessibilityService extends AccessibilityService {

    private static final String CHANNEL_ID = "clipcho_status";
    private static final int NOTIFICATION_ID = 1;
    private static final Pattern EN_COPY_BUTTON = Pattern.compile("\\b(copy|cut)\\b", Pattern.CASE_INSENSITIVE);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastTrigger = 0;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        showStatusNotification();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null) return;
        CharSequence pkg = e.getPackageName();
        if (pkg != null && getPackageName().contentEquals(pkg)) return;

        int type = e.getEventType();
        String text = collectText(e, type == AccessibilityEvent.TYPE_VIEW_CLICKED);
        if (text.isEmpty()) return;

        boolean hit;
        if (type == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            hit = isCopyButton(text);
        } else {
            hit = isCopiedMessage(text, pkg);
        }
        if (hit) trigger();
    }

    /** A tapped button whose label is short and says copy / cut. */
    static boolean isCopyButton(String s) {
        String t = s.trim();
        if (t.length() > 24) return false;
        return t.contains("コピー") || t.contains("切り取り") || t.contains("複製")
                || EN_COPY_BUTTON.matcher(t).find();
    }

    /** A toast / announcement / system popup saying something was copied. */
    static boolean isCopiedMessage(String s, CharSequence pkg) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.contains("コピーしました") || t.contains("コピーされました") || t.contains("コピー済み")
                || t.contains("クリップボードにコピー") || t.contains("copied")) return true;
        boolean systemUi = pkg != null && "com.android.systemui".contentEquals(pkg);
        return systemUi && (t.contains("クリップボード") || t.contains("clipboard"));
    }

    private String collectText(AccessibilityEvent e, boolean includeSource) {
        StringBuilder sb = new StringBuilder();
        List<CharSequence> parts = e.getText();
        if (parts != null) {
            for (CharSequence cs : parts) {
                if (cs != null) sb.append(cs).append(' ');
            }
        }
        if (e.getContentDescription() != null) sb.append(e.getContentDescription()).append(' ');
        if (includeSource && sb.toString().trim().isEmpty()) {
            try {
                AccessibilityNodeInfo src = e.getSource();
                if (src != null) {
                    if (src.getText() != null) sb.append(src.getText()).append(' ');
                    if (src.getContentDescription() != null) sb.append(src.getContentDescription());
                }
            } catch (Exception ignored) {
            }
        }
        return sb.toString().trim();
    }

    private void trigger() {
        long now = SystemClock.uptimeMillis();
        if (now - lastTrigger < 800) return;
        lastTrigger = now;
        // Give the other app a moment to actually put the text on the clipboard.
        handler.postDelayed(this::openReader, 350);
    }

    private void openReader() {
        try {
            Intent i = new Intent(this, ClipReaderActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void showStatusNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "自動保存の状態",
                    NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            ch.setDescription("自動保存が動いていることを表示し、「今のコピーを保存」ボタンを出します");
            nm.createNotificationChannel(ch);

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent save = new Intent(this, ClipReaderActivity.class);
            save.putExtra(ClipReaderActivity.EXTRA_MANUAL, true);
            save.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            PendingIntent savePi = PendingIntent.getActivity(this, 1, save,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_clip)
                    .setContentTitle("クリップ帳：自動保存中")
                    .setContentText("タップで履歴を開く")
                    .setContentIntent(openPi)
                    .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "今のコピーを保存", savePi).build())
                    .setOngoing(true)
                    .setShowWhen(false)
                    .build();
            nm.notify(NOTIFICATION_ID, n);
        } catch (Exception ignored) {
            // Notification permission may be off; auto-save still works.
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(NOTIFICATION_ID);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
