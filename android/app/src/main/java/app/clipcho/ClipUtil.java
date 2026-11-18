package app.clipcho;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

public final class ClipUtil {

    /** Label put on clips this app copies, so they are not saved again as "new". */
    public static final String SELF_LABEL = "clipcho";

    private ClipUtil() {
    }

    /**
     * Reads the current clipboard text. Only works while one of this app's windows has focus
     * (Android 10+ rule). Returns null for empty clips, our own copies and sensitive clips
     * such as passwords.
     */
    public static String readClipboard(Context ctx) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return null;
            ClipDescription desc = cd.getDescription();
            if (desc != null) {
                if (desc.getLabel() != null && SELF_LABEL.contentEquals(desc.getLabel())) return null;
                if (isSensitive(desc)) return null;
            }
            CharSequence cs = cd.getItemAt(0).coerceToText(ctx);
            return cs == null ? null : cs.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSensitive(ClipDescription desc) {
        PersistableBundle extras = desc.getExtras();
        if (extras == null) return false;
        if (Build.VERSION.SDK_INT >= 33 && extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)) return true;
        // Older convention used by some password managers
        return extras.getBoolean("android.content.extra.IS_SENSITIVE", false);
    }

    /** When this app last wrote the clipboard (uptime ms); the auto-save service ignores copies right after it. */
    public static volatile long lastSelfCopy = 0;

    public static boolean copy(Context ctx, String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return false;
            lastSelfCopy = android.os.SystemClock.uptimeMillis();
            cm.setPrimaryClip(ClipData.newPlainText(SELF_LABEL, text));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAutoSaveOn(Context ctx) {
        String enabled = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;
        ComponentName me = new ComponentName(ctx, ClipAccessibilityService.class);
        String full = me.flattenToString();
        String shortName = me.flattenToShortString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(full) || s.equalsIgnoreCase(shortName)) return true;
        }
        return false;
    }

    public static String rtrim(String s) {
        if (s == null) return null;
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    public static void toast(Context ctx, String msg) {
        Toast.makeText(ctx.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
