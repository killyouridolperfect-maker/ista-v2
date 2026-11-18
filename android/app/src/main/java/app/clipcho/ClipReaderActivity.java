package app.clipcho;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * An invisible screen that takes focus for a moment. Android 10+ only lets the focused app read
 * the clipboard, so the auto-save service opens this right after a "copy" is detected.
 */
public class ClipReaderActivity extends Activity {

    public static final String EXTRA_MANUAL = "manual";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean done = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Safety net: never stay on screen if focus does not arrive.
        handler.postDelayed(this::close, 2500);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || done) return;
        done = true;
        boolean manual = getIntent().getBooleanExtra(EXTRA_MANUAL, false);
        String text = ClipUtil.readClipboard(this);
        int r = ClipDb.get(this).add(text, !manual);
        if (r == ClipDb.RESULT_NEW) {
            ClipUtil.toast(this, "クリップ帳に保存しました");
        } else if (manual) {
            ClipUtil.toast(this, r == ClipDb.RESULT_DUP ? "保存済みです（一番上に移動しました）" : "コピー中の文字がありません");
        }
        close();
    }

    private void close() {
        handler.removeCallbacksAndMessages(null);
        if (!isFinishing()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
