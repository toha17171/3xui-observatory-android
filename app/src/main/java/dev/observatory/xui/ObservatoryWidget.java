package dev.observatory.xui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** Compact widget receiver, also handles refresh clicks for either widget size. */
public class ObservatoryWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH="dev.observatory.xui.REFRESH";
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        WidgetRenderer.updateAll(c);
        RefreshJob.schedule(c);
    }
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(ACTION_REFRESH.equals(intent.getAction())){
            PendingResult pending=goAsync();
            new Thread(()->{
                try{ Syncer.refreshAll(c.getApplicationContext()); }
                finally{pending.finish();}
            },"3xui-widget-refresh").start();
        }
    }
    @Override public void onDeleted(Context c,int[] ids){
        for(int id:ids)DashboardStore.removeWidget(c,id);
    }
    public static void updateAll(Context c){WidgetRenderer.updateAll(c);}
}
