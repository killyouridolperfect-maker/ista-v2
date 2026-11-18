package app.clipcho;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/** Clip history stored on the phone only (SQLite). */
public class ClipDb extends SQLiteOpenHelper {

    public static final int MAX = 2000;
    public static final int RESULT_EMPTY = 0;
    public static final int RESULT_NEW = 1;
    public static final int RESULT_DUP = 2;

    public static class Clip {
        public long id;
        public String text;
        public long ts;
        public boolean pin;
    }

    private static ClipDb instance;

    public static synchronized ClipDb get(Context c) {
        if (instance == null) instance = new ClipDb(c.getApplicationContext());
        return instance;
    }

    private ClipDb(Context c) {
        super(c, "clips.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE clips (id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, ts INTEGER NOT NULL, pin INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_clips_ts ON clips(ts)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    /**
     * Save a clip.
     * @param quietIfLatest when true and the text is already the newest clip, nothing changes
     *                      (used by automatic capture so false alarms do not reorder the list).
     */
    public synchronized int add(String raw, boolean quietIfLatest) {
        String text = ClipUtil.rtrim(raw);
        if (text == null || text.trim().isEmpty()) return RESULT_EMPTY;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();

        try (Cursor c = db.query("clips", new String[]{"id"}, "text = ?", new String[]{text},
                null, null, null, "1")) {
            if (c.moveToFirst()) {
                long id = c.getLong(0);
                if (quietIfLatest && id == latestId(db)) return RESULT_DUP;
                ContentValues v = new ContentValues();
                v.put("ts", now);
                db.update("clips", v, "id = ?", new String[]{String.valueOf(id)});
                return RESULT_DUP;
            }
        }

        ContentValues v = new ContentValues();
        v.put("text", text);
        v.put("ts", now);
        v.put("pin", 0);
        db.insert("clips", null, v);
        db.execSQL("DELETE FROM clips WHERE pin = 0 AND id NOT IN "
                + "(SELECT id FROM clips WHERE pin = 0 ORDER BY ts DESC LIMIT " + MAX + ")");
        return RESULT_NEW;
    }

    private long latestId(SQLiteDatabase db) {
        try (Cursor c = db.query("clips", new String[]{"id"}, null, null, null, null, "ts DESC", "1")) {
            return c.moveToFirst() ? c.getLong(0) : -1;
        }
    }

    public synchronized List<Clip> list(String query, boolean pinOnly) {
        List<Clip> out = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (pinOnly) where.append("pin = 1");
        if (query != null && !query.trim().isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("text LIKE ? ESCAPE '\\'");
            String q = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            args.add("%" + q + "%");
        }
        try (Cursor c = getReadableDatabase().query("clips", new String[]{"id", "text", "ts", "pin"},
                where.length() > 0 ? where.toString() : null,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, "ts DESC", String.valueOf(MAX + 500))) {
            while (c.moveToNext()) {
                Clip k = new Clip();
                k.id = c.getLong(0);
                k.text = c.getString(1);
                k.ts = c.getLong(2);
                k.pin = c.getInt(3) == 1;
                out.add(k);
            }
        }
        return out;
    }

    public synchronized int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM clips", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    public synchronized void setPin(long id, boolean pin) {
        ContentValues v = new ContentValues();
        v.put("pin", pin ? 1 : 0);
        getWritableDatabase().update("clips", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void updateText(long id, String text) {
        ContentValues v = new ContentValues();
        v.put("text", text);
        getWritableDatabase().update("clips", v, "id = ?", new String[]{String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete("clips", "id = ?", new String[]{String.valueOf(id)});
    }
}
