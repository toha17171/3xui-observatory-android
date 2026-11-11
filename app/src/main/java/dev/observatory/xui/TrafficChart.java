package dev.observatory.xui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Native Android Canvas sparkline, no WebView or graphics dependencies. */
public final class TrafficChart {
    private TrafficChart() {}
    private static final int BLUE=Color.rgb(22,119,255);
    private static final int GREEN=Color.rgb(82,196,26);
    private static final int GRID=Color.rgb(61,67,79);

    public static Bitmap draw(Context c,String panelId,Set<String> selection,int width,int height) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        JSONArray history=DashboardStore.history(c,panelId);
        int start=Math.max(1,history.length()-48);
        List<Double> up=new ArrayList<>(),down=new ArrayList<>();
        for(int i=start;i<history.length();i++){
            JSONObject prev=history.optJSONObject(i-1);
            JSONObject now=history.optJSONObject(i);
            if(prev==null||now==null)continue;
            double seconds=(now.optLong("t")-prev.optLong("t"))/1000d;
            if(seconds<=0)continue;
            JSONObject before=prev.optJSONObject("values"),after=now.optJSONObject("values");
            if(before==null||after==null)continue;
            double u=0,d=0;
            JSONArray keys=after.names();
            if(keys==null)continue;
            for(int k=0;k<keys.length();k++){
                String key=keys.optString(k);
                if(!selection.isEmpty()&&!selection.contains(key))continue;
                JSONObject old=before.optJSONObject(key),cur=after.optJSONObject(key);
                if(old==null||cur==null)continue;
                long oldU=old.optLong("up"),oldD=old.optLong("down");
                long newU=cur.optLong("up"),newD=cur.optLong("down");
                if(newU>=oldU)u+=newU-oldU;
                if(newD>=oldD)d+=newD-oldD;
            }
            up.add(u/seconds);
            down.add(d/seconds);
        }
        float left=20,top=18,right=width-20,bottom=height-20;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(GRID);
        for(int i=0;i<4;i++){
            float y=top+(bottom-top)*i/3;
            canvas.drawLine(left,y,right,y,p);
        }
        if(up.size()<2){
            p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(144,152,170));p.setTextSize(25f);
            canvas.drawText("История появится после обновлений",20,height/2f,p);
            return bitmap;
        }
        double max=1;
        for(double d:up)max=Math.max(max,d);
        for(double d:down)max=Math.max(max,d);
        plot(canvas,p,down,max,left,top,right,bottom,BLUE);
        plot(canvas,p,up,max,left,top,right,bottom,GREEN);
        return bitmap;
    }
    private static void plot(Canvas c,Paint p,List<Double> values,double max,
                             float left,float top,float right,float bottom,int color){
        Path path=new Path();
        for(int i=0;i<values.size();i++){
            float x=left+(right-left)*(i/(float)(values.size()-1));
            float y=bottom-(float)(values.get(i)/max)*(bottom-top);
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        p.setColor(color);p.setStrokeWidth(4f);p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStyle(Paint.Style.STROKE);
        c.drawPath(path,p);
        p.setStyle(Paint.Style.FILL);
        float y=bottom-(float)(values.get(values.size()-1)/max)*(bottom-top);
        c.drawCircle(right,y,5f,p);
    }
}
