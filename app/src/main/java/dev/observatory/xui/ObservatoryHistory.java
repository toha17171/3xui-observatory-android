package dev.observatory.xui;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Cached time series from the official 3x-ui ObservatoryHistory API.
 * Stored without tokens. Local samples are kept as a fallback when an older
 * panel has no history endpoint.
 */
public final class ObservatoryHistory {
    private static final String FILE="observatory_history_v12";
    private ObservatoryHistory() {}
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    private static String key(String panel,String tag) {
        // Each tag is isolated by an opaque string key, never part of an URL without encoding.
        return "hist_"+panel+"_"+tag;
    }
    private static JSONArray read(Context c,String key) {
        try {return new JSONArray(prefs(c).getString(key,"[]"));}
        catch(Exception e) {return new JSONArray();}
    }
    public static JSONArray data(Context c,String panel,String tag) {
        JSONArray remote=read(c,key(panel,tag)+"_remote");
        if(remote.length()>1)return remote;
        return read(c,key(panel,tag)+"_local");
    }
    /** Retain at most 128 recent samples; remote input is validated before caching. */
    public static void save(Context c,String panel,String tag,JSONArray series) {
        JSONArray filtered=new JSONArray();
        int start=Math.max(0,series.length()-128);
        for(int i=start;i<series.length();i++) {
            JSONObject sample=series.optJSONObject(i);
            if(sample==null)continue;
            long t=sample.optLong("t",-1);
            double v=sample.optDouble("v",Double.NaN);
            if(t<=0||Double.isNaN(v)||Double.isInfinite(v))continue;
            try {
                JSONObject point=new JSONObject();
                point.put("t",t);point.put("v",v);
                filtered.put(point);
            } catch(Exception ignored) {}
        }
        if(filtered.length()>0)prefs(c).edit().putString(key(panel,tag)+"_remote",filtered.toString()).apply();
    }
    /** Append local observations even when history API is temporarily unavailable. */
    public static synchronized void recordSnapshot(Context c,String panel,JSONArray snapshot) {
        long now=System.currentTimeMillis()/1000L;
        SharedPreferences.Editor e=prefs(c).edit();
        for(int i=0;i<snapshot.length();i++) {
            JSONObject entry=snapshot.optJSONObject(i);
            if(entry==null)continue;
            String tag=entry.optString("tag","");
            if(tag.isEmpty()||tag.length()>128)continue;
            String storage=key(panel,tag)+"_local";
            JSONArray prev=read(c,storage);
            int last=prev.length()-1;
            if(last>=0) {
                JSONObject latest=prev.optJSONObject(last);
                if(latest!=null && now-latest.optLong("t")<25) continue;
            }
            JSONArray next=new JSONArray();
            for(int x=Math.max(0,prev.length()-127);x<prev.length();x++)next.put(prev.opt(x));
            try {
                JSONObject point=new JSONObject();
                point.put("t",now);
                point.put("v",entry.optBoolean("alive",false)
                       ?Math.max(0,entry.optLong("delay",0)):-1);
                next.put(point);
                e.putString(storage,next.toString());
            } catch(Exception ignored) {}
        }
        e.apply();
    }
    /** Download histories of selected outbounds, bounded to four calls per refresh. */
    public static void refreshSelected(Context c,Store.Panel panel,JSONArray snapshot,String token) {
        Set<String> selected=new LinkedHashSet<>();
        // Prefer each widget's actual chart focus, before any unselected global tags.
        int[] compact=android.appwidget.AppWidgetManager.getInstance(c).getAppWidgetIds(
             new android.content.ComponentName(c,ObservatoryWidget.class));
        int[] expanded=android.appwidget.AppWidgetManager.getInstance(c).getAppWidgetIds(
             new android.content.ComponentName(c,ExpandedWidget.class));
        for(int[] ids:new int[][]{compact,expanded})for(int id:ids) {
            if(!panel.id.equals(DashboardStore.panelForWidget(c,id)))continue;
            String focus=ObservatorySettings.focus(c,id);
            if(!focus.isEmpty())selected.add(focus);
            Set<String> tags=ObservatorySettings.forWidget(c,id,panel.id);
            if(!tags.isEmpty())selected.addAll(tags);
        }
        selected.addAll(ObservatorySettings.forPanel(c,panel.id));
        if(selected.isEmpty()) {
            for(int i=0;i<Math.min(3,snapshot.length());i++){
                JSONObject o=snapshot.optJSONObject(i);
                if(o!=null) selected.add(o.optString("tag",""));
            }
        }
        int downloaded=0;
        for(String tag:selected) {
            if(tag.isEmpty()||tag.length()>128)continue;
            if(!contains(snapshot,tag))continue;
            try{
                JSONArray history=fetch(panel.url,token,tag,ObservatorySettings.bucket(c,panel.id));
                save(c,panel.id,tag,history);
            }catch(Exception ignored) {
                // Latest observatory snapshot continues to be displayed.
            }
            if(++downloaded>=4)break;
        }
    }
    private static boolean contains(JSONArray array,String tag) {
        for(int i=0;i<array.length();i++) {
            JSONObject o=array.optJSONObject(i);
            if(o!=null && tag.equals(o.optString("tag")))return true;
        }
        return false;
    }
    /** GET /panel/api/server/xrayObservatoryHistory/:tag/:bucket. */
    public static JSONArray fetch(String base,String token,String tag,int bucket) throws Exception {
        if(token==null||token.trim().isEmpty()) throw new IllegalArgumentException("No token");
        String escaped=URLEncoder.encode(tag,"UTF-8").replace("+","%20");
        String route="panel/api/server/xrayObservatoryHistory/"+escaped+"/"+bucket;
        URL url=URI.create(ObservatoryApi.baseUrl(base)).resolve(route).toURL();
        HttpURLConnection con=(HttpURLConnection)url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(7000);
        con.setReadTimeout(9000);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Authorization","Bearer "+token.trim());
        con.setRequestProperty("Accept","application/json");
        try {
            if(con.getResponseCode()!=200)throw new IllegalStateException("HTTP "+con.getResponseCode());
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream in=con.getInputStream()){
                byte[] b=new byte[4096];int n;
                while((n=in.read(b))!=-1) {
                    output.write(b,0,n);
                    if(output.size()>400000)throw new IllegalStateException("History too large");
                }
            }
            JSONObject root=new JSONObject(new String(output.toByteArray(),StandardCharsets.UTF_8));
            if(!root.optBoolean("success"))throw new IllegalStateException("3x-ui history error");
            JSONArray data=root.optJSONArray("obj");
            return data==null?new JSONArray():data;
        }finally{con.disconnect();}
    }
}
