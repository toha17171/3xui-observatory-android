package dev.observatory.xui;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Public inbound summaries and local chart history, separated from encrypted credentials. */
public final class DashboardStore {
    private DashboardStore() {}
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("observatory_dashboard_v2",Context.MODE_PRIVATE);
    }
    public static JSONArray inbounds(Context c,String panelId) {
        try { return new JSONArray(prefs(c).getString("inbounds_"+panelId,"[]")); }
        catch(Exception e){ return new JSONArray(); }
    }
    public static String inboundError(Context c,String panelId) {
        return prefs(c).getString("inbound_error_"+panelId,"");
    }
    public static void inboundError(Context c,String id,String error) {
        prefs(c).edit().putString("inbound_error_"+id,error).apply();
    }
    public static JSONArray history(Context c,String id) {
        try { return new JSONArray(prefs(c).getString("history_"+id,"[]")); }
        catch(Exception e){ return new JSONArray(); }
    }
    public static synchronized void updateInbounds(Context c,String id,JSONArray rows) {
        long now=System.currentTimeMillis();
        JSONObject totals=new JSONObject();
        for(int i=0;i<rows.length();i++){
            JSONObject inbound=rows.optJSONObject(i);
            if(inbound==null)continue;
            try {
                JSONObject pair=new JSONObject();
                pair.put("up",inbound.optLong("up"));
                pair.put("down",inbound.optLong("down"));
                totals.put(String.valueOf(inbound.optInt("id")),pair);
            }catch(Exception ignored){}
        }
        JSONArray old=history(c,id);
        JSONArray newer=new JSONArray();
        int start=Math.max(0,old.length()-94);
        for(int i=start;i<old.length();i++)newer.put(old.opt(i));
        if(newer.length()==0 || now-newer.optJSONObject(newer.length()-1).optLong("t")>30000) {
            try {JSONObject sample=new JSONObject();sample.put("t",now);sample.put("values",totals);newer.put(sample);}
            catch(Exception ignored){}
        }
        prefs(c).edit().putString("inbounds_"+id,rows.toString())
          .putString("history_"+id,newer.toString()).remove("inbound_error_"+id).apply();
    }
    public static Set<String> selectedInbounds(Context c,String panelId){
        return decode(prefs(c).getString("selection_"+panelId,"[]"));
    }
    /** Empty selection means show all inbounds. */
    public static void selectInbounds(Context c,String panelId,Set<String> ids) {
        prefs(c).edit().putString("selection_"+panelId,encode(ids)).apply();
    }
    public static String panelForWidget(Context c,int appWidgetId) {
        String id=prefs(c).getString("widget_panel_"+appWidgetId,"");
        if(!id.isEmpty()){
            for(Store.Panel p:Store.panels(c))if(p.id.equals(id))return id;
        }
        Store.Panel active=Store.selected(c);
        return active==null?"":active.id;
    }
    public static Set<String> selectionForWidget(Context c,int appWidgetId,String panelId){
        String key="widget_ids_"+appWidgetId;
        if(prefs(c).contains(key))return decode(prefs(c).getString(key,"[]"));
        return selectedInbounds(c,panelId);
    }
    public static void configureWidget(Context c,int widgetId,String panelId,Set<String> ids){
        prefs(c).edit().putString("widget_panel_"+widgetId,panelId)
            .putString("widget_ids_"+widgetId,encode(ids)).apply();
    }
    public static void removeWidget(Context c,int widgetId){
        prefs(c).edit().remove("widget_panel_"+widgetId).remove("widget_ids_"+widgetId).apply();
        ObservatorySettings.removeWidget(c,widgetId);
    }
    public static void removePanel(Context c,String id){
        prefs(c).edit().remove("inbounds_"+id).remove("history_"+id)
          .remove("inbound_error_"+id).remove("selection_"+id).apply();
        ObservatorySettings.removePanel(c,id);
    }
    private static Set<String> decode(String s){
        Set<String> result=new HashSet<>();
        try {JSONArray a=new JSONArray(s);for(int i=0;i<a.length();i++)result.add(a.getString(i));}
        catch(Exception ignored){}
        return result;
    }
    private static String encode(Set<String> items){
        JSONArray a=new JSONArray();for(String s:items)a.put(s);return a.toString();
    }
    public static List<JSONObject> visibleInbounds(Context c,String panelId,Set<String> ids){
        JSONArray arr=inbounds(c,panelId);
        List<JSONObject> list=new ArrayList<>();
        for(int i=0;i<arr.length();i++){
            JSONObject row=arr.optJSONObject(i);
            if(row!=null&&(ids.isEmpty()||ids.contains(String.valueOf(row.optInt("id")))))list.add(row);
        }
        return list;
    }
    public static String formatBytes(long value){
        if(value<1024)return value+" B";
        String[] u={"KB","MB","GB","TB"};
        double v=value;
        int i=-1;do{v/=1024;i++;}while(v>=1024&&i<u.length-1);
        return String.format(Locale.getDefault(),"%.1f %s",v,u[i]);
    }
    public static Store.Panel getPanel(Context c,String id){
        for(Store.Panel p:Store.panels(c))if(p.id.equals(id))return p;
        return Store.selected(c);
    }
}
