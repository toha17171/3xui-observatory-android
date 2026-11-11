package dev.observatory.xui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Renders two widget sizes from the same read-only local cache. */
public final class WidgetRenderer {
    private WidgetRenderer(){}
    private static final int TEXT=Color.rgb(231,235,243);
    private static final int GREEN=Color.rgb(82,196,26);
    private static final int RED=Color.rgb(255,77,79);
    private static final int MUTED=Color.rgb(157,165,178);
    public static void updateAll(Context c){
        AppWidgetManager mgr=AppWidgetManager.getInstance(c);
        int[] small=mgr.getAppWidgetIds(new ComponentName(c,ObservatoryWidget.class));
        int[] large=mgr.getAppWidgetIds(new ComponentName(c,ExpandedWidget.class));
        for(int id:small)update(c,id,false);
        for(int id:large)update(c,id,true);
    }
    public static void update(Context c,int id,boolean large){
        AppWidgetManager mgr=AppWidgetManager.getInstance(c);
        RemoteViews v=new RemoteViews(c.getPackageName(),
              large?R.layout.widget_expanded:R.layout.widget_compact);
        setupActions(c,v,id,large);
        String pid=DashboardStore.panelForWidget(c,id);
        Store.Panel panel=DashboardStore.getPanel(c,pid);
        String title=panel==null?"3x-ui":panel.name;
        v.setTextViewText(large?R.id.w_large_title:R.id.w_compact_title,title);
        if(panel==null){
            v.setTextViewText(large?R.id.w_large_summary:R.id.w_compact_primary,"Настройте сервер");
            mgr.updateAppWidget(id,v);return;
        }
        Set<String> selected=DashboardStore.selectionForWidget(c,id,panel.id);
        List<JSONObject> rows=DashboardStore.visibleInbounds(c,panel.id,selected);
        int enabled=0;
        long up=0,down=0;
        for(JSONObject r:rows){
            if(r.optBoolean("enable"))enabled++;
            up+=Math.max(0,r.optLong("up"));down+=Math.max(0,r.optLong("down"));
        }
        String counts=enabled+"/"+rows.size()+" включены";
        String usage="↓ "+DashboardStore.formatBytes(down)+"   ↑ "+DashboardStore.formatBytes(up);
        String updated=Store.lastOk(c,panel.id)==0?"Нет обновлений":
            new SimpleDateFormat("dd.MM HH:mm",Locale.getDefault()).format(new Date(Store.lastOk(c,panel.id)));
        String issue=DashboardStore.inboundError(c,panel.id);
        if(!issue.isEmpty())updated+=" · inbound недоступны";
        if(large){
            v.setTextViewText(R.id.w_large_summary,"INBOUND · "+counts);
            v.setTextViewText(R.id.w_large_totals,usage);
            v.setTextViewText(R.id.w_large_footer,"Обновлено "+updated);
            v.setImageViewBitmap(R.id.w_large_chart,TrafficChart.draw(c,panel.id,selected,640,175));
            int[] labels={R.id.w_large_line1,R.id.w_large_line2,R.id.w_large_line3};
            for(int i=0;i<labels.length;i++){
                if(i<rows.size()){
                    JSONObject r=rows.get(i);
                    String desc=(r.optBoolean("enable")?"●  ":"○  ")+display(r)+" · "+r.optInt("port");
                    v.setTextViewText(labels[i],desc);
                    v.setTextColor(labels[i],r.optBoolean("enable")?TEXT:RED);
                    v.setViewVisibility(labels[i],View.VISIBLE);
                }else v.setViewVisibility(labels[i],View.GONE);
            }
            if(rows.size()>3)v.setTextViewText(R.id.w_large_more,"+ ещё "+(rows.size()-3));
            else v.setTextViewText(R.id.w_large_more,"");
            JSONArray ob=Store.cached(c,panel.id);
            int alive=0;long delay=0;
            for(int i=0;i<ob.length();i++){
                JSONObject r=ob.optJSONObject(i);
                if(r!=null&&r.optBoolean("alive")){alive++;delay+=r.optLong("delay");}
            }
            v.setTextViewText(R.id.w_large_outbound,ob.length()==0?"OUTBOUND · пока нет данных":
                  "OUTBOUND · "+alive+"/"+ob.length()+" онлайн"+
                  (alive>0?" · "+(delay/alive)+" мс средняя":""));
        }else{
            v.setTextViewText(R.id.w_compact_primary,"INBOUND  "+counts);
            v.setTextViewText(R.id.w_compact_second,rows.size()==0?
                "Нажмите ↗ для выбора":usage);
            v.setTextViewText(R.id.w_compact_footer,updated);
        }
        mgr.updateAppWidget(id,v);
    }
    private static String display(JSONObject inbound){
        String remark=inbound.optString("remark").trim();
        return remark.isEmpty()?inbound.optString("protocol","inbound"):remark;
    }
    private static void setupActions(Context c,RemoteViews v,int id,boolean large){
        int root=large?R.id.w_large_root:R.id.w_compact_root;
        int reload=large?R.id.w_large_refresh:R.id.w_compact_refresh;
        int configure=large?R.id.w_large_settings:R.id.w_compact_settings;
        Intent launch=new Intent(c,MainActivity.class).setAction("dev.observatory.xui.OPEN_WIDGET");
        PendingIntent open=PendingIntent.getActivity(c,10000+id,launch,
             PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(root,open);
        Intent refresh=new Intent(c,ObservatoryWidget.class).setAction(ObservatoryWidget.ACTION_REFRESH);
        PendingIntent pi=PendingIntent.getBroadcast(c,20000+id,refresh,
             PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(reload,pi);
        Intent conf=new Intent(c,WidgetConfigureActivity.class)
             .setAction("dev.observatory.xui.CONFIGURE");
        conf.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        PendingIntent config=PendingIntent.getActivity(c,30000+id,conf,
             PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(configure,config);
    }
}
