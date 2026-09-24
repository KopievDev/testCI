package dev.kopiev.bridgenotes;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class SyncClient {
    private SyncClient() { }

    public static JSONArray sync(String baseUrl, String token, List<Note> notes) throws Exception {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        URL url = new URL(normalized + "/api/sync");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(6000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("X-Bridge-Token", token == null ? "" : token.trim());

        JSONArray arr = new JSONArray();
        for (Note n : notes) arr.put(n.toJson());
        JSONObject root = new JSONObject();
        root.put("notes", arr);
        byte[] body = root.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = c.getOutputStream()) { os.write(body); }

        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        StringBuilder text = new StringBuilder();
        if (stream != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) text.append(line);
            }
        }
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + text);
        return new JSONObject(text.toString()).optJSONArray("notes");
    }
}
