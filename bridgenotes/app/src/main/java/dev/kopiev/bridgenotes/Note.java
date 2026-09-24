package dev.kopiev.bridgenotes;

import org.json.JSONException;
import org.json.JSONObject;

public final class Note {
    public String id;
    public String title;
    public String body;
    public boolean pinned;
    public boolean deleted;
    public long createdAt;
    public long updatedAt;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title == null ? "" : title);
        o.put("body", body == null ? "" : body);
        o.put("pinned", pinned);
        o.put("deleted", deleted);
        o.put("createdAt", createdAt);
        o.put("updatedAt", updatedAt);
        return o;
    }

    public static Note fromJson(JSONObject o) {
        Note n = new Note();
        n.id = o.optString("id", "");
        n.title = o.optString("title", "");
        n.body = o.optString("body", "");
        n.pinned = o.optBoolean("pinned", false);
        n.deleted = o.optBoolean("deleted", false);
        n.createdAt = o.optLong("createdAt", System.currentTimeMillis());
        n.updatedAt = o.optLong("updatedAt", n.createdAt);
        return n;
    }
}
