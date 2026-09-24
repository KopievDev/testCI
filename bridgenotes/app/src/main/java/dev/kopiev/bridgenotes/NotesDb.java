package dev.kopiev.bridgenotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class NotesDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "bridge_notes.db";
    private static final int DB_VERSION = 1;

    public NotesDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (" +
                "id TEXT PRIMARY KEY," +
                "title TEXT NOT NULL DEFAULT ''," +
                "body TEXT NOT NULL DEFAULT ''," +
                "pinned INTEGER NOT NULL DEFAULT 0," +
                "deleted INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public void upsert(Note n) {
        ContentValues v = new ContentValues();
        v.put("id", n.id);
        v.put("title", n.title == null ? "" : n.title);
        v.put("body", n.body == null ? "" : n.body);
        v.put("pinned", n.pinned ? 1 : 0);
        v.put("deleted", n.deleted ? 1 : 0);
        v.put("created_at", n.createdAt);
        v.put("updated_at", n.updatedAt);
        getWritableDatabase().insertWithOnConflict("notes", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Note get(String id) {
        try (Cursor c = getReadableDatabase().query("notes", null, "id=?", new String[]{id}, null, null, null)) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    public List<Note> listVisible(String search) {
        ArrayList<Note> out = new ArrayList<>();
        String q = search == null ? "" : search.trim();
        String selection = "deleted=0";
        String[] args = null;
        if (!q.isEmpty()) {
            selection += " AND (title LIKE ? OR body LIKE ?)";
            String like = "%" + q + "%";
            args = new String[]{like, like};
        }
        try (Cursor c = getReadableDatabase().query("notes", null, selection, args, null, null, "pinned DESC, updated_at DESC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public List<Note> listAllIncludingDeleted() {
        ArrayList<Note> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("notes", null, null, null, null, null, "updated_at DESC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    public void mergeRemote(Note remote) {
        Note local = get(remote.id);
        if (local == null || remote.updatedAt > local.updatedAt) upsert(remote);
    }

    private Note fromCursor(Cursor c) {
        Note n = new Note();
        n.id = c.getString(c.getColumnIndexOrThrow("id"));
        n.title = c.getString(c.getColumnIndexOrThrow("title"));
        n.body = c.getString(c.getColumnIndexOrThrow("body"));
        n.pinned = c.getInt(c.getColumnIndexOrThrow("pinned")) != 0;
        n.deleted = c.getInt(c.getColumnIndexOrThrow("deleted")) != 0;
        n.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        n.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        return n;
    }
}
