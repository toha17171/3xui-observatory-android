package dev.observatory.xui;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Read-only inbound summary adapter. Never persists settings.clients or credentials. */
public final class InboundsApi {
    private InboundsApi() {}
    private static JSONArray request(String base, String token, String path) throws Exception {
        if(token == null || token.trim().isEmpty()) throw new IllegalArgumentException("API-токен не задан");
        URL url = URI.create(ObservatoryApi.baseUrl(base)).resolve(path).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(10000);
        con.setReadTimeout(12000);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Authorization", "Bearer " + token.trim());
        con.setRequestProperty("Accept", "application/json");
        try {
            int code = con.getResponseCode();
            if(code == 404) throw new MissingEndpointException();
            if(code == 401 || code == 403) throw new IllegalStateException("Inbound API: HTTP " + code + " (проверьте разрешения токена)");
            if(code != 200) throw new IllegalStateException("Inbound API: HTTP " + code);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try(InputStream in = con.getInputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    if(out.size() > 4*1024*1024) throw new IllegalStateException("Слишком большой ответ inbound API");
                }
            }
            JSONObject root = new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            if(!root.optBoolean("success")) throw new IllegalStateException(root.optString("msg", "3x-ui: ошибка inbound API"));
            JSONArray a = root.optJSONArray("obj");
            if(a == null) throw new IllegalStateException("3x-ui: inbound obj не является массивом");
            return a;
        } finally { con.disconnect(); }
    }
    private static final class MissingEndpointException extends Exception {}

    /** GET /list/slim; legacy /list fallback (sensitive fields discarded immediately). */
    public static JSONArray fetch(String base, String token) throws Exception {
        JSONArray source;
        try { source = request(base, token, "panel/api/inbounds/list/slim"); }
        catch(MissingEndpointException e) { source = request(base, token, "panel/api/inbounds/list"); }
        JSONArray sanitized = new JSONArray();
        for(int i=0;i<source.length();i++) {
            JSONObject in=source.optJSONObject(i); if(in==null) continue;
            JSONObject item=new JSONObject();
            item.put("id", in.optInt("id", -1));
            item.put("remark", in.optString("remark", ""));
            item.put("protocol", in.optString("protocol", ""));
            item.put("port", in.optInt("port", 0));
            item.put("enable", in.optBoolean("enable", false));
            item.put("up", Math.max(0, in.optLong("up",0)));
            item.put("down", Math.max(0, in.optLong("down",0)));
            item.put("total", Math.max(0, in.optLong("total",0)));
            if(item.optInt("id",-1)>=0) sanitized.put(item);
        }
        return sanitized;
    }
}
