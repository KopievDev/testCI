package dev.kopiev.bridgenotes;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncEngine {
    private static final AtomicBoolean syncing = new AtomicBoolean(false);

    public interface Callback {
        void onSuccess(int noteCount);
        void onError(Exception error);
    }

    private SyncEngine() { }

    public static boolean isConfigured(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("bridge_notes", Context.MODE_PRIVATE);
        return !prefs.getString("sync_url", "").trim().isEmpty()
                && !prefs.getString("token", "").trim().isEmpty();
    }

    public static void syncAsync(Context context, Callback callback) {
        new Thread(() -> {
            try {
                int count = syncBlocking(context);
                if (callback != null) callback.onSuccess(count);
            } catch (Exception e) {
                if (callback != null) callback.onError(e);
            }
        }, "bridge-sync").start();
    }

    public static int syncBlocking(Context context) throws Exception {
        if (!syncing.compareAndSet(false, true)) return -1;
        try {
            SharedPreferences prefs = context.getSharedPreferences("bridge_notes", Context.MODE_PRIVATE);
            String url = prefs.getString("sync_url", "").trim();
            String token = prefs.getString("token", "").trim();
            if (url.isEmpty() || token.isEmpty()) throw new IllegalStateException("Синхронизация не настроена");

            NotesDb db = new NotesDb(context.getApplicationContext());
            JSONArray remote = SyncClient.sync(url, token, db.listAllIncludingDeleted());
            int count = remote == null ? 0 : remote.length();
            if (remote != null) {
                for (int i = 0; i < remote.length(); i++) {
                    db.mergeRemote(Note.fromJson(remote.getJSONObject(i)));
                }
            }
            db.close();
            prefs.edit().putLong("last_sync", System.currentTimeMillis()).apply();
            return count;
        } finally {
            syncing.set(false);
        }
    }
}
