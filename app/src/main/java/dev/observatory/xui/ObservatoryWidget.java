package dev.observatory.xui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ObservatoryWidget extends AppWidgetProvider {
    private static final String REFRESH="dev.observatory.xui.REFRESH";
    private static final int[] ROW={R.id.widget_row1,R.id.widget_row2,R.id.widget_row3,R.id.widget_row4};
    private static final int[] TAG={R.id.widget_tag1,R.id.widget_tag2,R.id.widget_tag3,R.id.widget_tag4};
    private static final int[] DELAY={R.id.widget_delay1,R.id.widget_delay2,R.id.widget_delay3,R.id.widget_delay4};

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        updateAll(c);RefreshJob.schedule(c);
    }
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(REFRESH.equals(intent.getAction())) {
            PendingResult pending=goAsync();
            new Thread(()->{
                try{Syncer.refreshAll(c.getApplicationContext());}finally{pending.finish();}
            },"ObservatoryWidgetRefresh").start();
        }
    }
    public static void updateAll(Context c){
        AppWidgetManager mgr=AppWidgetManager.getInstance(c);
        ComponentName component=new ComponentName(c,ObservatoryWidget.class);
        int[] ids=mgr.getAppWidgetIds(component);
        for(int id:ids)mgr.updateAppWidget(id,views(c));
    }
    private static RemoteViews views(Context c){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_observatory);
        Store.Panel panel=Store.selected(c);
        String name=panel==null?"3x-ui Observatory":panel.name;
        v.setTextViewText(R.id.widget_title,name);
        Intent open=new Intent(c,MainActivity.class);
        PendingIntent openPending=PendingIntent.getActivity(c,0,open,
          PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root,openPending);
        Intent refresh=new Intent(c,ObservatoryWidget.class).setAction(REFRESH);
        PendingIntent reload=PendingIntent.getBroadcast(c,1,refresh,
          PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_refresh,reload);
        for(int i=0;i<4;i++)v.setViewVisibility(ROW[i],View.GONE);
        if(panel==null){
            v.setTextViewText(R.id.widget_summary,"Настройте сервер в приложении");
            v.setTextViewText(R.id.widget_timestamp,"Нажмите для настройки");
            return v;
        }
        JSONArray data=Store.cached(c,panel.id);
        List<JSONObject> sorted=new ArrayList<>();
        int online=0;
        for(int i=0;i<data.length();i++){
            JSONObject obj=data.optJSONObject(i);
            if(obj!=null){sorted.add(obj);if(obj.optBoolean("alive"))online++;}
        }
        Collections.sort(sorted,(a,b)->{
            if(a.optBoolean("alive")!=b.optBoolean("alive"))return a.optBoolean("alive")?1:-1;
            return a.optString("tag").compareToIgnoreCase(b.optString("tag"));
        });
        String error=Store.error(c,panel.id);
        if(data.length()==0)v.setTextViewText(R.id.widget_summary,error.isEmpty()?"Нет данных Observatory":"Ошибка подключения");
        else v.setTextViewText(R.id.widget_summary,online+" / "+sorted.size()+" доступны"+(error.isEmpty()?"":" · нет соединения"));
        for(int i=0;i<Math.min(4,sorted.size());i++){
            JSONObject o=sorted.get(i);
            boolean alive=o.optBoolean("alive");
            v.setViewVisibility(ROW[i],View.VISIBLE);
            v.setTextViewText(TAG[i],(alive?"●  ":"●  ")+o.optString("tag","?"));
            v.setTextColor(TAG[i],alive?Color.rgb(213,229,243):Color.rgb(255,153,153));
            v.setTextViewText(DELAY[i],alive?o.optLong("delay")+" мс":"DOWN");
            v.setTextColor(DELAY[i],alive?Color.rgb(112,220,177):Color.rgb(255,122,122));
        }
        long ts=Store.lastOk(c,panel.id);
        String time=ts==0?"Нет успешных обновлений":"Обновлено "+new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(ts));
        if(!error.isEmpty())time+=" · ошибка";
        v.setTextViewText(R.id.widget_timestamp,time);
        return v;
    }
}
