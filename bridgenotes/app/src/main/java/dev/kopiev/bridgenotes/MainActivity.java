package dev.kopiev.bridgenotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class MainActivity extends Activity {
    private NotesDb db;
    private NoteAdapter adapter;
    private EditText search;
    private TextView status;
    private SharedPreferences prefs;
    private final List<Note> notes = new ArrayList<>();
    private final Handler foregroundSync = new Handler(Looper.getMainLooper());
    private boolean resumed;

    private static final long FOREGROUND_SYNC_MS = 20_000L;

    private final Runnable periodicSync = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            autoSync(false);
            foregroundSync.postDelayed(this, FOREGROUND_SYNC_MS);
        }
    };

    private static final int BG = Color.rgb(248, 250, 252);
    private static final int CARD = Color.WHITE;
    private static final int PRIMARY = Color.rgb(79, 70, 229);
    private static final int TEXT = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        db = new NotesDb(this);
        prefs = getSharedPreferences("bridge_notes", MODE_PRIVATE);
        AutoSyncScheduler.ensureScheduled(this);
        setContentView(buildUi());
        refresh();
        updateIdleStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refresh();
        foregroundSync.removeCallbacks(periodicSync);
        foregroundSync.postDelayed(periodicSync, 600L);
    }

    @Override protected void onPause() {
        resumed = false;
        foregroundSync.removeCallbacks(periodicSync);
        super.onPause();
    }

    @Override protected void onDestroy() {
        foregroundSync.removeCallbacksAndMessages(null);
        if (db != null) db.close();
        super.onDestroy();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(12));
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("BridgeNotes");
        title.setTextColor(TEXT);
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));

        top.addView(actionButton("⟳", v -> syncNow()));
        top.addView(actionButton("⚙", v -> showSettings()));
        root.addView(top);

        search = new EditText(this);
        search.setHint("Поиск заметок");
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(roundRect(Color.WHITE, 16, Color.rgb(226, 232, 240), 1));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        status = new TextView(this);
        status.setText("Автосинхронизация включена");
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setPadding(dp(2), dp(8), 0, dp(7));
        root.addView(status);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(80));
        adapter = new NoteAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> editNote(notes.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            confirmDelete(notes.get(position));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        Button add = new Button(this);
        add.setText("＋  Новая заметка");
        add.setTextColor(Color.WHITE);
        add.setTextSize(16);
        add.setAllCaps(false);
        add.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add.setBackground(roundRect(PRIMARY, 18, PRIMARY, 0));
        add.setOnClickListener(v -> editNote(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        addParams.topMargin = dp(8);
        root.addView(add, addParams);
        return root;
    }

    private Button actionButton(String text, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(21);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundRect(Color.TRANSPARENT, 14, Color.TRANSPARENT, 0));
        b.setOnClickListener(click);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(48), dp(48));
        p.leftMargin = dp(4);
        b.setLayoutParams(p);
        return b;
    }

    private void refresh() {
        if (db == null || adapter == null) return;
        String q = search == null ? "" : search.getText().toString();
        notes.clear();
        notes.addAll(db.listVisible(q));
        adapter.notifyDataSetChanged();
    }

    private void editNote(Note original) {
        boolean isNew = original == null;
        Note draft = isNew ? new Note() : original;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText title = new EditText(this);
        title.setHint("Заголовок");
        title.setSingleLine(true);
        title.setText(isNew ? "" : draft.title);
        title.setTextSize(19);
        box.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        EditText body = new EditText(this);
        body.setHint("Текст заметки");
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        body.setMinLines(8);
        body.setText(isNew ? "" : draft.body);
        box.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)));

        CheckBox pin = new CheckBox(this);
        pin.setText("Закрепить заметку");
        pin.setChecked(!isNew && draft.pinned);
        box.addView(pin);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isNew ? "Новая заметка" : "Редактирование")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String b = body.getText().toString().trim();
            if (t.isEmpty() && b.isEmpty()) {
                Toast.makeText(this, "Введите текст заметки", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            if (isNew) {
                draft.id = UUID.randomUUID().toString();
                draft.createdAt = now;
            }
            draft.title = t;
            draft.body = b;
            draft.pinned = pin.isChecked();
            draft.deleted = false;
            draft.updatedAt = now;
            db.upsert(draft);
            dialog.dismiss();
            refresh();
            status.setText("Сохранено локально • отправляю на Mac…");
            autoSync(false);
        }));
        dialog.show();
    }

    private void confirmDelete(Note n) {
        String message = (n.title == null || n.title.trim().isEmpty())
                ? "Заметка будет удалена на всех синхронизированных устройствах."
                : n.title;

        new AlertDialog.Builder(this)
                .setTitle("Удалить заметку?")
                .setMessage(message)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (d, w) -> {
                    n.deleted = true;
                    n.updatedAt = System.currentTimeMillis();
                    db.upsert(n);
                    refresh();
                    status.setText("Удалено локально • синхронизирую…");
                    autoSync(false);
                })
                .show();
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);

        EditText url = new EditText(this);
        url.setHint("http://192.168.1.10:8787");
        url.setText(prefs.getString("sync_url", ""));
        url.setSingleLine(true);
        box.addView(label("Адрес Mac"));
        box.addView(url, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        EditText token = new EditText(this);
        token.setHint("Код подключения");
        token.setText(prefs.getString("token", ""));
        token.setSingleLine(true);
        box.addView(label("Код подключения"));
        box.addView(token, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView tip = label("BridgeNotes 2.0 синхронизируется автоматически. Пока используется локальная Wi‑Fi сеть; кнопка ⟳ остаётся для ручной проверки.");
        tip.setPadding(0, dp(12), 0, 0);
        box.addView(tip);

        new AlertDialog.Builder(this)
                .setTitle("Синхронизация с Mac")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", (d, w) -> {
                    prefs.edit()
                            .putString("sync_url", url.getText().toString().trim())
                            .putString("token", token.getText().toString().trim())
                            .apply();
                    AutoSyncScheduler.ensureScheduled(this);
                    status.setText("Настройки сохранены • проверяю соединение…");
                    autoSync(true);
                })
                .show();
    }

    private TextView label(String s) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextColor(MUTED);
        v.setTextSize(13);
        return v;
    }

    private void syncNow() {
        if (!SyncEngine.isConfigured(this)) {
            showSettings();
            return;
        }
        status.setText("Синхронизация…");
        autoSync(true);
    }

    private void autoSync(boolean showToast) {
        if (!SyncEngine.isConfigured(this)) {
            updateIdleStatus();
            return;
        }
        SyncEngine.syncAsync(getApplicationContext(), new SyncEngine.Callback() {
            @Override public void onSuccess(int noteCount) {
                runOnUiThread(() -> {
                    refresh();
                    long ts = prefs.getLong("last_sync", System.currentTimeMillis());
                    status.setText("Автосинхронизация • " +
                            DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(ts)));
                    if (showToast && noteCount >= 0) {
                        Toast.makeText(MainActivity.this, "Синхронизировано", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override public void onError(Exception error) {
                runOnUiThread(() -> {
                    status.setText("Mac недоступен • повторю автоматически");
                    if (showToast) {
                        Toast.makeText(MainActivity.this,
                                "Не удалось синхронизироваться: " + error.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void updateIdleStatus() {
        if (status == null) return;
        if (!SyncEngine.isConfigured(this)) {
            status.setText("Локально • нажмите ⚙ для подключения Mac");
            return;
        }
        long last = prefs.getLong("last_sync", 0L);
        if (last == 0L) status.setText("Автосинхронизация включена");
        else status.setText("Автосинхронизация • " +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(last)));
    }

    private final class NoteAdapter extends BaseAdapter {
        private final Context context;
        NoteAdapter(Context c) { context = c; }

        @Override public int getCount() { return notes.size(); }
        @Override public Object getItem(int position) { return notes.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            Note n = notes.get(position);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(13), dp(16), dp(13));
            card.setBackground(roundRect(CARD, 18, Color.rgb(226, 232, 240), 1));

            TextView t = new TextView(context);
            String title = (n.title == null || n.title.trim().isEmpty()) ? firstLine(n.body) : n.title;
            t.setText((n.pinned ? "★  " : "") + title);
            t.setTextColor(TEXT);
            t.setTextSize(17);
            t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            t.setMaxLines(1);
            card.addView(t);

            if (n.body != null && !n.body.trim().isEmpty()) {
                TextView b = new TextView(context);
                b.setText(n.body.replace('\n', ' '));
                b.setTextColor(MUTED);
                b.setTextSize(14);
                b.setMaxLines(2);
                b.setPadding(0, dp(6), 0, 0);
                card.addView(b);
            }

            TextView date = new TextView(context);
            date.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(n.updatedAt)));
            date.setTextColor(Color.rgb(148, 163, 184));
            date.setTextSize(11);
            date.setPadding(0, dp(8), 0, 0);
            card.addView(date);
            return card;
        }
    }

    private String firstLine(String s) {
        if (s == null || s.trim().isEmpty()) return "Без названия";
        String x = s.trim().replace('\n', ' ');
        return x.length() > 40 ? x.substring(0, 40) + "…" : x;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
