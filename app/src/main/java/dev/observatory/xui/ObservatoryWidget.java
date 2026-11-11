package dev.observatory.xui;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;
import android.content.Intent;

/** Compact widget receiver, also handles refresh clicks for either widget size. */
public class ObservatoryWidget extends AppWidgetProvider {
    public static final String ACTION_REFRESH="dev.observatory.xui.REFRESH";
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){
        WidgetRenderer.updateAll(c);
        RefreshJob.schedule(c);
    }
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager mgr,
                                                        int id,Bundle options) {
        super.onAppWidgetOptionsChanged(c,mgr,id,options);
        // A resized compact widget upgrades/downgrades its layout immediately.
        WidgetRenderer.update(c,id);
    }
    @Override public void onReceive(Context c,Intent intent){
        super.onReceive(c,intent);
        if(ACTION_REFRESH.equals(intent.getAction())) {
            // Avoid holding a broadcast PendingResult for multiple HTTP history requests.
            RefreshJob.requestNow(c.getApplicationContext());
        }
    }

    @Override public void onDeleted(Context c,int[] ids){
        for(int id:ids)DashboardStore.removeWidget(c,id);
    }
    public static void updateAll(Context c){WidgetRenderer.updateAll(c);}
}
