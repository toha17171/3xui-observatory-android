package dev.observatory.xui;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Background refresh and state-transition alerts; never marks API errors as node failures. */
public final class Syncer {
    private static final String CHANNEL="outbounds";
    private Syncer(){}
    public static void refreshAll(Context context){
        List<Store.Panel> ps=Store.panels(context);
        for(Store.Panel panel:ps) refreshOne(context,panel);
        ObservatoryWidget.updateAll(context);
    }
    /** Observatory and inbound endpoints refresh independently: either may fail without hiding the other. */
    public static String refreshOne(Context context, Store.Panel panel){
        String observationError=null,inboundError=null;
        try {
            JSONArray old=Store.cached(context,panel.id);
            JSONArray current=ObservatoryApi.fetch(panel.url,Store.token(context,panel.id));
            Store.updateCache(context,panel.id,current);
            if(Store.notifications(context)) transitions(context,panel,old,current);
        }catch(Exception e){
            observationError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            Store.updateError(context,panel.id,observationError);
        }
        try {
            JSONArray current=InboundsApi.fetch(panel.url,Store.token(context,panel.id));
            DashboardStore.updateInbounds(context,panel.id,current);
        }catch(Exception e){
            inboundError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
            DashboardStore.inboundError(context,panel.id,inboundError);
        }
        ObservatoryWidget.updateAll(context);
        if(observationError==null && inboundError==null)return null;
        return (observationError==null?"":"Observatory: "+observationError)+
               (observationError!=null&&inboundError!=null?" | ":"")+
               (inboundError==null?"":"Inbounds: "+inboundError);
    }
    private static void transitions(Context c,Store.Panel panel,JSONArray old,JSONArray now){
        if (old.length()==0) return;
        Map<String,Boolean> previous=new HashMap<>();
        for(int i=0;i<old.length();i++){
            JSONObject a=old.optJSONObject(i);if(a!=null)previous.put(a.optString("tag"),a.optBoolean("alive"));
        }
        for(int i=0;i<now.length();i++){
            JSONObject a=now.optJSONObject(i);if(a==null)continue;
            String tag=a.optString("tag");Boolean was=previous.get(tag);
            boolean alive=a.optBoolean("alive");
            if(was==null||was==alive)continue;
            if(alive&&!Store.recovery(c))continue;
            notify(c,panel.name,tag,alive);
        }
    }
    private static void notify(Context c,String panel,String tag,boolean alive){
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Состояние outbound",NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent=new Intent(c,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String description=panel+" · "+tag+(alive?" восстановлен":" недоступен");
        android.app.Notification notice=new android.app.Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_observatory)
                .setContentTitle(alive?"Outbound снова доступен":"Outbound недоступен")
                .setContentText(description).setContentIntent(pi).setAutoCancel(true).build();
        nm.notify((panel+"/"+tag).hashCode(),notice);
    }
}
