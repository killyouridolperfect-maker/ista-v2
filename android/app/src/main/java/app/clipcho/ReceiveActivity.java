package app.clipcho;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Handles "クリップ帳に保存" in the text-selection menu and the share sheet. */
public class ReceiveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        String text = null;
        if (in != null) {
            if (Intent.ACTION_PROCESS_TEXT.equals(in.getAction())) {
                CharSequence cs = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
                text = cs == null ? null : cs.toString();
            } else if (Intent.ACTION_SEND.equals(in.getAction())) {
                CharSequence cs = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
                text = cs != null ? cs.toString() : in.getStringExtra(Intent.EXTRA_SUBJECT);
            }
        }
        int r = ClipDb.get(this).add(text, false);
        if (r == ClipDb.RESULT_NEW) ClipUtil.toast(this, "クリップ帳に保存しました");
        else if (r == ClipDb.RESULT_DUP) ClipUtil.toast(this, "保存済みです（一番上に移動しました）");
        else ClipUtil.toast(this, "保存できる文字がありません");
        setResult(RESULT_CANCELED); // never change the selected text in the other app
        finish();
        overridePendingTransition(0, 0);
    }
}
