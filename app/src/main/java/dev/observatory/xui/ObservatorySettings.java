package dev.observatory.xui;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Per-panel and per-widget observatory selections; empty selection means all outbounds. */
public final class ObservatorySettings {
    private static final String FILE = "observatory_presentation_v12";
    private ObservatorySettings() {}
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    private static Set<String> parse(String raw) {
        Set<String> set = new LinkedHashSet<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i=0;i<arr.length();i++) {
                String tag=arr.optString(i,"");
                if (!tag.isEmpty()) set.add(tag);
            }
        } catch (Exception ignored) {}
        return set;
    }
    private static String encode(Set<String> tags) {
        JSONArray a=new JSONArray();
        for (String s:tags) a.put(s);
        return a.toString();
    }
    public static Set<String> forPanel(Context c,String panel) {
        return parse(prefs(c).getString("panel_tags_"+panel,"[]"));
    }
    public static void savePanel(Context c,String panel,Set<String> tags) {
        prefs(c).edit().putString("panel_tags_"+panel,encode(tags)).apply();
    }
    public static Set<String> forWidget(Context c,int widgetId,String panel) {
        String key="widget_tags_"+widgetId;
        return prefs(c).contains(key)
              ? parse(prefs(c).getString(key,"[]")) : forPanel(c,panel);
    }
    public static void saveWidget(Context c,int id,Set<String> tags,int chart,String mainTag,
                                   boolean showProbe,boolean showTraffic) {
        prefs(c).edit()
          .putString("widget_tags_"+id,encode(tags))
          .putInt("widget_chart_"+id,chart)
          .putString("widget_focus_"+id,mainTag)
          .putBoolean("widget_probe_"+id,showProbe)
          .putBoolean("widget_traffic_"+id,showTraffic).apply();
    }
    /** 0 = outbound latency, 1 = availability, 2 = inbound traffic. */
    public static int chart(Context c,int id) {
        return prefs(c).getInt("widget_chart_"+id,0);
    }
    public static String focus(Context c,int id) {
        return prefs(c).getString("widget_focus_"+id,"");
    }
    public static boolean showProbe(Context c,int id) {
        return prefs(c).getBoolean("widget_probe_"+id,true);
    }
    public static boolean showTraffic(Context c,int id) {
        return prefs(c).getBoolean("widget_traffic_"+id,true);
    }
    public static int bucket(Context c,String panel) {
        int v=prefs(c).getInt("bucket_"+panel,120);
        return v==30||v==120||v==300?v:120;
    }
    public static void bucket(Context c,String panel,int bucket) {
        if(bucket==30||bucket==120||bucket==300)
            prefs(c).edit().putInt("bucket_"+panel,bucket).apply();
    }
    public static void removeWidget(Context c,int widgetId) {
        prefs(c).edit().remove("widget_tags_"+widgetId).remove("widget_chart_"+widgetId)
            .remove("widget_focus_"+widgetId).remove("widget_probe_"+widgetId)
            .remove("widget_traffic_"+widgetId).apply();
    }
    public static void removePanel(Context c,String panelId) {
        prefs(c).edit().remove("panel_tags_"+panelId).remove("bucket_"+panelId).apply();
    }
    public static List<JSONObject> filtered(JSONArray source,Set<String> selected) {
        List<JSONObject> rows=new ArrayList<>();
        for(int i=0;i<source.length();i++) {
            JSONObject r=source.optJSONObject(i);
            if(r==null)continue;
            String tag=r.optString("tag","");
            if(tag.isEmpty()||(!selected.isEmpty()&&!selected.contains(tag)))continue;
            rows.add(r);
        }
        Collections.sort(rows,(a,b)->{
            boolean aa=a.optBoolean("alive"),bb=b.optBoolean("alive");
            if(aa!=bb)return aa?1:-1;
            return a.optString("tag").compareToIgnoreCase(b.optString("tag"));
        });
        return rows;
    }
}
