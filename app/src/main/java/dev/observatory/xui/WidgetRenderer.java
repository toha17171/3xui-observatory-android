package dev.observatory.xui;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A single stable RemoteViews layout for every size and provider.
 * Resizing changes visibility/row count but never switches layout IDs, so
 * launchers do not lose their already-rendered views during reinflation.
 */
public final class WidgetRenderer {
    private WidgetRenderer() {}
    private static final String LOG="3xuiWidget";
    private static final int GREEN=0xFF52C41A;
    private static final int RED=0xFFFF696B;
    private static final int BLUE=0xFF69ADFF;
    private static final int AMBER=0xFFFFBC65;
    private static final int WHITE=0xFFF2F5FA;
    private static final int MUTED=0xFFA4AEBC;
    private static final int[] ROWS={
        R.id.w_node_row1,R.id.w_node_row2,R.id.w_node_row3,R.id.w_node_row4,
        R.id.w_node_row5,R.id.w_node_row6,R.id.w_node_row7,R.id.w_node_row8
    };
    private static final int[] NAMES={
        R.id.w_node_name1,R.id.w_node_name2,R.id.w_node_name3,R.id.w_node_name4,
        R.id.w_node_name5,R.id.w_node_name6,R.id.w_node_name7,R.id.w_node_name8
    };
    private static final int[] DELAYS={
        R.id.w_node_delay1,R.id.w_node_delay2,R.id.w_node_delay3,R.id.w_node_delay4,
        R.id.w_node_delay5,R.id.w_node_delay6,R.id.w_node_delay7,R.id.w_node_delay8
    };

    public static void updateAll(Context c) {
        AppWidgetManager manager=AppWidgetManager.getInstance(c);
        for(Class<?> component:new Class<?>[]{ObservatoryWidget.class,ExpandedWidget.class}) {
            for(int id:manager.getAppWidgetIds(new ComponentName(c,component))) {
                try { update(c,id); }
                catch(RuntimeException e) {Log.e(LOG,"Widget "+id+" update failed",e);}
            }
        }
    }
    public static void update(Context c,int id,boolean unused) {update(c,id);}

    private static int heightDp(Context c,AppWidgetManager manager,int id) {
        Bundle opts=manager.getAppWidgetOptions(id);
        AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        int min=opts==null?0:opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,0);
        int max=opts==null?0:opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,0);
        boolean portrait=c.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_LANDSCAPE;
        int height=portrait?Math.max(min,max):(min>0?min:max);
        if(height<60) {
            boolean expanded=info!=null && info.provider!=null &&
                    info.provider.getClassName().endsWith(".ExpandedWidget");
            height=expanded?315:150;
        }
        return Math.min(700,Math.max(100,height));
    }
    public static void update(Context c,int id) {
        AppWidgetManager mgr=AppWidgetManager.getInstance(c);
        int height=heightDp(c,mgr,id);
        RemoteViews views=new RemoteViews(c.getPackageName(),R.layout.widget_dashboard);
        actions(c,views,id);
        String panelId=DashboardStore.panelForWidget(c,id);
        Store.Panel panel=DashboardStore.getPanel(c,panelId);
        String panelName=panel==null?"3x-ui Observatory":panel.name;
        views.setTextViewText(R.id.w_title,panelName);
        reset(views);
        if(panel==null) {
            views.setTextViewText(R.id.w_status,"Настройте сервер в приложении");
            views.setTextViewText(R.id.w_average,"");
            views.setTextViewText(R.id.w_footer,"Нажмите ⚙ для настроек");
            mgr.updateAppWidget(id,views);
            return;
        }
        Set<String> tags=ObservatorySettings.forWidget(c,id,panel.id);
        List<JSONObject> entries=ObservatorySettings.filtered(Store.cached(c,panel.id),tags);
        int online=0,min=Integer.MAX_VALUE,max=0;
        long sum=0;
        for(JSONObject o:entries){
            if(!o.optBoolean("alive"))continue;
            int delay=(int)Math.max(0,Math.min(600000,o.optLong("delay",0)));
            if(delay<=0)continue;
            online++;sum+=delay;min=Math.min(min,delay);max=Math.max(max,delay);
        }
        int down=entries.size()-online;
        boolean error=Store.error(c,panel.id).length()>0;
        views.setTextViewText(R.id.w_status,entries.isEmpty()
               ?"● OBSERVATORY · нет данных":"● OBSERVATORY  "+online+"/"+entries.size()+(down==0?"":" · "+down+" DOWN"));
        views.setTextColor(R.id.w_status,entries.isEmpty()?MUTED:down>0?AMBER:GREEN);
        views.setTextViewText(R.id.w_average,online>0?"AVG "+(sum/online)+" мс":"—");
        if(height>=164) {
            views.setViewVisibility(R.id.w_stats,View.VISIBLE);
            views.setTextViewText(R.id.w_stats,online>0?
                    "MIN "+min+" мс     MAX "+max+" мс     DOWN "+down:
                    "Проверка соединений: "+(error?"ошибка API":"данных нет"));
        }
        boolean showTraffic=height>=182 && ObservatorySettings.showTraffic(c,id);
        views.setViewVisibility(R.id.w_traffic,showTraffic?View.VISIBLE:View.GONE);
        List<JSONObject> inbounds=DashboardStore.visibleInbounds(c,panel.id,
                DashboardStore.selectionForWidget(c,id,panel.id));
        if(showTraffic){
            int enabled=0;long up=0,downBytes=0;
            for(JSONObject item:inbounds) {
                if(item.optBoolean("enable"))enabled++;
                up+=Math.max(0,item.optLong("up"));
                downBytes+=Math.max(0,item.optLong("down"));
            }
            views.setTextViewText(R.id.w_traffic,
                    "IN "+enabled+"/"+inbounds.size()+"   ↓ "+DashboardStore.formatBytes(downBytes)+
                    "   ↑ "+DashboardStore.formatBytes(up));
        }
        // Reserve chart space only where there is room for both metrics and a graph.
        boolean showChart=height>=310 && !entries.isEmpty();
        views.setViewVisibility(R.id.w_chart_section,showChart?View.VISIBLE:View.GONE);
        int reserved=94+(height>=164?20:0)+(showTraffic?22:0)+(showChart?82:0);
        int capacity=Math.min(8,Math.max(1,(height-reserved)/24));
        int shown=Math.min(capacity,entries.size());
        for(int i=0;i<ROWS.length;i++) {
            boolean visible=i<shown;
            views.setViewVisibility(ROWS[i],visible?View.VISIBLE:View.GONE);
            if(!visible)continue;
            JSONObject item=entries.get(i);
            boolean alive=item.optBoolean("alive");
            String tag=item.optString("tag","Outbound");
            String alias=readableTag(tag);
            views.setTextViewText(NAMES[i],withDot(alias,alive));
            int delay=(int)item.optLong("delay");
            views.setTextViewText(DELAYS[i],alive?delay+" мс":"DOWN");
            views.setTextColor(DELAYS[i],!alive?RED:delay>750?AMBER:BLUE);
        }
        long last=Store.lastOk(c,panel.id);
        String updated=last>0?new SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(last)):"—";
        views.setTextViewText(R.id.w_updated,updated);
        boolean stale=last>0 && System.currentTimeMillis()-last>
                Math.max(35,Store.interval(c)*3)*60000L;
        String footer="Обновлено "+updated+(stale?" · устарело":"");
        if(shown<entries.size()) footer+="   •   ещё "+(entries.size()-shown)+" узл.";
        if(error)footer+="   •   API error";
        views.setTextViewText(R.id.w_footer,footer);
        views.setTextColor(R.id.w_footer,error||stale?AMBER:MUTED);

        if(showChart) {
            String focus=ObservatorySettings.focus(c,id);
            if(focus.length()==0 || !contains(entries,focus))focus=entries.get(0).optString("tag");
            int mode=ObservatorySettings.chart(c,id);
            String prefix=mode==2?"ТРАФИК INBOUND":mode==1?"ДОСТУПНОСТЬ":"ЗАДЕРЖКА";
            views.setTextViewText(R.id.w_chart_label,prefix+"  ·  "+readableTag(focus));
            try {
                views.setImageViewBitmap(R.id.w_chart,
                    ObservatoryChart.draw(c,panel.id,focus,mode,
                        DashboardStore.selectionForWidget(c,id,panel.id),440,110));
            }catch(RuntimeException e){
                Log.w(LOG,"Chart could not be rendered",e);
                views.setViewVisibility(R.id.w_chart_section,View.GONE);
            }
        }
        // If an OEM launcher rejects the bitmap, retry the SAME layout with
        // the chart hidden; never swap RemoteViews layout IDs while resizing.
        try {mgr.updateAppWidget(id,views);}
        catch(RuntimeException e) {
            Log.w(LOG,"Widget update failed, retrying without chart",e);
            if(!showChart)throw e;
            views.setViewVisibility(R.id.w_chart_section,View.GONE);
            mgr.updateAppWidget(id,views);
        }
    }
    private static boolean contains(List<JSONObject> values,String tag){
        for(JSONObject v:values)if(tag.equals(v.optString("tag")))return true;
        return false;
    }
    private static String readableTag(String s){
        if(s==null)return "Outbound";
        // Only simplify common duplicate transport suffix; leave unique names intact.
        String t=s.replaceAll("(?i)(?:[ _-]tailscale){2}$"," · TS");
        return t.length()>70?t.substring(0,69)+"…":t;
    }
    private static CharSequence withDot(String label,boolean alive){
        String s="●  "+label;
        SpannableString span=new SpannableString(s);
        span.setSpan(new ForegroundColorSpan(alive?GREEN:RED),0,1,33);
        return span;
    }
    private static void reset(RemoteViews v) {
        v.setViewVisibility(R.id.w_stats,View.GONE);
        v.setViewVisibility(R.id.w_traffic,View.GONE);
        v.setViewVisibility(R.id.w_chart_section,View.GONE);
        for(int row:ROWS)v.setViewVisibility(row,View.GONE);
    }
    private static void actions(Context c,RemoteViews v,int id) {
        Intent open=new Intent(c,MainActivity.class).setAction("dev.observatory.xui.OPEN_WIDGET");
        PendingIntent openPi=PendingIntent.getActivity(c,10000+id,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_dashboard_root,openPi);
        Intent refresh=new Intent(c,ObservatoryWidget.class)
                .setAction(ObservatoryWidget.ACTION_REFRESH);
        PendingIntent refreshPi=PendingIntent.getBroadcast(c,20000+id,refresh,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_refresh,refreshPi);
        Intent configure=new Intent(c,WidgetConfigureActivity.class)
                .setAction("dev.observatory.xui.CONFIGURE");
        configure.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        PendingIntent configPi=PendingIntent.getActivity(c,30000+id,configure,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_config,configPi);
    }
}
