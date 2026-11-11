package dev.observatory.xui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Adaptive home-screen dashboard; every instance retains its own settings. */
public final class WidgetRenderer {
    private WidgetRenderer(){}
    private static final int WHITE=0xFFF4F6FB;
    private static final int GREEN=0xFF52C41A;
    private static final int RED=0xFFFF686B;
    private static final int BLUE=0xFF57A6FF;
    private static final int MUTED=0xFFA4AAB6;

    /** Redraw compact and large providers, including widgets resized to another layout. */
    public static void updateAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        for(Class<?> provider:new Class<?>[]{ObservatoryWidget.class,ExpandedWidget.class}) {
            int[] ids=manager.getAppWidgetIds(new ComponentName(c,provider));
            for(int id:ids) {
                try {update(c,id);} catch(RuntimeException ignored) {/* next widget should render */}
            }
        }
    }
    /** Old calls with an explicit size remain source compatible. */
    public static void update(Context c,int id,boolean ignored) {update(c,id);}

    public static void update(Context c,int id) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        boolean preferredLarge=info!=null && info.provider!=null &&
                   info.provider.getClassName().endsWith(".ExpandedWidget");
        Bundle options=manager.getAppWidgetOptions(id);
        int minH=options==null?0:options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0);
        // A 2x2 widget may be resized to 4x4 on the launcher: upgrade its layout.
        boolean large=preferredLarge || minH>=185;
        RemoteViews views=new RemoteViews(c.getPackageName(),
                large?R.layout.widget_expanded:R.layout.widget_compact);
        actions(c,views,id,large);
        String panelId=DashboardStore.panelForWidget(c,id);
        Store.Panel panel=DashboardStore.getPanel(c,panelId);
        views.setTextViewText(large?R.id.w_large_title:R.id.w_compact_title,
                panel==null?"3x-ui Observatory":panel.name);
        if(panel==null) {
            views.setTextViewText(large?R.id.w_large_summary:R.id.w_compact_primary,
                    "Нужно добавить сервер");
            manager.updateAppWidget(id,views);return;
        }
        JSONArray all=Store.cached(c,panel.id);
        Set<String> selected=ObservatorySettings.forWidget(c,id,panel.id);
        List<JSONObject> obs=ObservatorySettings.filtered(all,selected);
        int alive=0;
        for(JSONObject r:obs)if(r.optBoolean("alive"))alive++;
        String summary=obs.isEmpty()?"Нет данных Observatory":
                "OBSERVATORY  "+alive+"/"+obs.size()+"  •  "+(obs.size()-alive)+" DOWN";
        List<JSONObject> in=DashboardStore.visibleInbounds(c,panel.id,
                DashboardStore.selectionForWidget(c,id,panel.id));
        long down=0,up=0;int inboundAlive=0;
        for(JSONObject r:in) {
            if(r.optBoolean("enable"))inboundAlive++;
            down+=Math.max(0,r.optLong("down"));
            up+=Math.max(0,r.optLong("up"));
        }
        String traffic="IN  "+inboundAlive+"/"+in.size()+"   ↓ "+DashboardStore.formatBytes(down)+
             "   ↑ "+DashboardStore.formatBytes(up);
        long update=Store.lastOk(c,panel.id);
        String time=update==0?"Обновление не выполнялось":
            new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(update));
        String stale=update>0&&System.currentTimeMillis()-update>
             Math.max(35,Store.interval(c)*3)*60000L?" · данные устарели":"";
        if(!large) {
            views.setTextViewText(R.id.w_compact_primary,summary);
            int[] ids={R.id.w_compact_line1,R.id.w_compact_line2};
            for(int i=0;i<ids.length;i++) {
                if(i<obs.size()) {
                    JSONObject r=obs.get(i);
                    views.setTextViewText(ids[i],styled(r,20,
                            ObservatorySettings.showProbe(c,id)));
                    views.setViewVisibility(ids[i],View.VISIBLE);
                }else{
                    views.setTextViewText(ids[i],i==0?"Выберите outbound в ⚙":"");
                    views.setViewVisibility(ids[i],i==0?View.VISIBLE:View.GONE);
                }
            }
            views.setTextViewText(R.id.w_compact_footer,
                ObservatorySettings.showTraffic(c,id) && !in.isEmpty()
                     ?traffic:("Обновлено "+time+stale));
        } else {
            views.setTextViewText(R.id.w_large_summary,summary);
            int[] ids={R.id.w_large_line1,R.id.w_large_line2,R.id.w_large_line3};
            for(int i=0;i<ids.length;i++) {
                if(i<obs.size()) {
                    views.setTextViewText(ids[i],styled(obs.get(i),27,
                          ObservatorySettings.showProbe(c,id)));
                    views.setViewVisibility(ids[i],View.VISIBLE);
                }else{
                    views.setTextViewText(ids[i],i==0?"Нет выбранных outbound":"");
                    views.setViewVisibility(ids[i],i==0?View.VISIBLE:View.GONE);
                }
            }
            String focus=ObservatorySettings.focus(c,id);
            if(focus.isEmpty() && !obs.isEmpty())focus=obs.get(0).optString("tag");
            boolean found=false;
            for(JSONObject r:obs)if(focus.equals(r.optString("tag")))found=true;
            if(!found && !obs.isEmpty())focus=obs.get(0).optString("tag");
            int mode=ObservatorySettings.chart(c,id);
            int bucket=ObservatorySettings.bucket(c,panel.id);
            String label=mode==2?"ТРАФИК INBOUND • ↓ / ↑":
              mode==1?"ДОСТУПНОСТЬ · "+shorten(focus,25):
                      "ЗАДЕРЖКА · "+shorten(focus,25);
            views.setTextViewText(R.id.w_large_chart_title,label+"  · "+bucket+"с");
            views.setImageViewBitmap(R.id.w_large_chart,
                ObservatoryChart.draw(c,panel.id,focus,mode,
                    DashboardStore.selectionForWidget(c,id,panel.id),460,150));
            boolean showTraffic=ObservatorySettings.showTraffic(c,id);
            views.setViewVisibility(R.id.w_large_totals,showTraffic?View.VISIBLE:View.GONE);
            if(showTraffic)views.setTextViewText(R.id.w_large_totals,traffic);
            views.setTextViewText(R.id.w_large_footer,"Последнее обновление "+time+stale+
                 (obs.size()>3?"  ·  ещё "+(obs.size()-3)+" → ⚙":""));
        }
        try {
            manager.updateAppWidget(id,views);
        } catch(RuntimeException e) {
            // Some launchers reject large RemoteViews bitmaps. Retry with a small view.
            if(large){
                RemoteViews safe=new RemoteViews(c.getPackageName(),R.layout.widget_compact);
                actions(c,safe,id,false);
                safe.setTextViewText(R.id.w_compact_title,panel.name);
                safe.setTextViewText(R.id.w_compact_primary,summary);
                safe.setTextViewText(R.id.w_compact_line1,
                    obs.isEmpty()?"Нет данных":styled(obs.get(0),20,false));
                safe.setViewVisibility(R.id.w_compact_line2,View.GONE);
                safe.setTextViewText(R.id.w_compact_footer,"Обновлено "+time);
                manager.updateAppWidget(id,safe);
            }else throw e;
        }
    }
    private static String shorten(String source,int max) {
        if(source==null)return "";
        return source.length()<=max?source:source.substring(0,max-1)+"…";
    }
    private static CharSequence styled(JSONObject node,int max,boolean time) {
        boolean alive=node.optBoolean("alive");
        String tag=shorten(node.optString("tag","Outbound"),max);
        String suffix=alive?node.optLong("delay")+" мс":"DOWN";
        if(time && node.optLong("lastTryTime")>1000000000L){
            suffix+=" · "+new SimpleDateFormat("HH:mm",Locale.getDefault())
                    .format(new Date(node.optLong("lastTryTime")*1000L));
        }
        String s="● "+tag+"    "+suffix;
        SpannableString value=new SpannableString(s);
        value.setSpan(new ForegroundColorSpan(alive?GREEN:RED),0,1,33);
        int start=s.indexOf(suffix);
        if(start>=0)value.setSpan(new ForegroundColorSpan(alive?BLUE:RED),
                        start,s.length(),33);
        return value;
    }
    private static void actions(Context c,RemoteViews views,int id,boolean large) {
        int root=large?R.id.w_large_root:R.id.w_compact_root;
        int refresh=large?R.id.w_large_refresh:R.id.w_compact_refresh;
        int configure=large?R.id.w_large_settings:R.id.w_compact_settings;
        Intent open=new Intent(c,MainActivity.class)
              .setAction("dev.observatory.xui.OPEN_WIDGET");
        PendingIntent openPi=PendingIntent.getActivity(c,10000+id,open,
              PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(root,openPi);
        Intent reload=new Intent(c,ObservatoryWidget.class)
             .setAction(ObservatoryWidget.ACTION_REFRESH);
        PendingIntent reloadPi=PendingIntent.getBroadcast(c,20000+id,reload,
              PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(refresh,reloadPi);
        Intent config=new Intent(c,WidgetConfigureActivity.class)
             .setAction("dev.observatory.xui.CONFIGURE");
        config.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        PendingIntent configPi=PendingIntent.getActivity(c,30000+id,config,
              PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(configure,configPi);
    }
}
