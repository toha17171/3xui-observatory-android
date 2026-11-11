package dev.observatory.xui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;

/** Canvas rendering inspired by 3x-ui's dark charts; no React/web runtime in widgets. */
public final class ObservatoryChart {
    private ObservatoryChart(){}
    private static final int TEXT=Color.rgb(163,169,183);
    private static final int GRID=Color.rgb(61,70,84);
    private static final int BLUE=Color.rgb(22,119,255);
    private static final int GREEN=Color.rgb(82,196,26);

    public static Bitmap draw(Context c,String panel,String tag,int mode,int width,int height) {
        return draw(c,panel,tag,mode,DashboardStore.selectedInbounds(c,panel),width,height);
    }
    public static Bitmap draw(Context c,String panel,String tag,int mode,Set<String> inbounds,int width,int height) {
        if(mode==2)return TrafficChart.draw(c,panel,inbounds,width,height);
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(result);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float left=46f,right=width-13f,top=15f,bottom=height-28f;
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1f);paint.setColor(GRID);
        for(int j=0;j<4;j++){
            float y=top+(bottom-top)*(j/3f);
            canvas.drawLine(left,y,right,y,paint);
        }
        JSONArray points=ObservatoryHistory.data(c,panel,tag);
        if(points.length()==0){
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(19f);paint.setColor(TEXT);
            canvas.drawText("Нет истории · обновите данные",left+5,bottom-13,paint);
            return result;
        }
        int count=points.length();
        double max=mode==1?1:20;
        for(int i=0;i<count;i++){
            JSONObject obj=points.optJSONObject(i);if(obj==null)continue;
            if(mode==0)max=Math.max(max,obj.optDouble("v",0));
        }
        if(mode==0)max=Math.ceil(max/25d)*25;
        long from=points.optJSONObject(0)==null?0:points.optJSONObject(0).optLong("t");
        long until=points.optJSONObject(count-1)==null?0:points.optJSONObject(count-1).optLong("t");
        if(until<=from)until=from+1;
        int main=mode==1?GREEN:BLUE;
        Path area=new Path();
        Path line=new Path();
        boolean segment=false,has=false;
        for(int i=0;i<count;i++){
            JSONObject point=points.optJSONObject(i);
            if(point==null)continue;
            long t=point.optLong("t");
            double raw=point.optDouble("v",-1);
            double value=mode==1?(raw>=0?1:0):raw;
            float x=left+(right-left)*Math.max(0,Math.min(1,(t-from)/(float)(until-from)));
            if(value<0){segment=false;continue;}
            float y=bottom-(float)Math.min(1,value/max)*(bottom-top);
            if(!segment){line.moveTo(x,y);segment=true;}
            else line.lineTo(x,y);
            has=true;
        }
        // Shade from the bottom to the curve; avoid filling across probe failures.
        if(has && count>=2) {
            boolean valid=true;
            Path fill=new Path();
            boolean began=false;
            for(int i=0;i<count;i++){
                JSONObject point=points.optJSONObject(i);if(point==null)continue;
                double raw=point.optDouble("v",-1);
                double v=mode==1?(raw>=0?1:0):raw;
                float x=left+(right-left)*Math.max(0,Math.min(1,(point.optLong("t")-from)/(float)(until-from)));
                if(v<0){valid=false;break;}
                float y=bottom-(float)Math.min(1,v/max)*(bottom-top);
                if(!began){fill.moveTo(x,bottom);fill.lineTo(x,y);began=true;}
                else fill.lineTo(x,y);
                if(i==count-1){fill.lineTo(x,bottom);fill.close();}
            }
            if(valid && began){
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(0,top,0,bottom,
                    mode==1?0x3352C41A:0x331677FF,0x0023252B,Shader.TileMode.CLAMP));
                canvas.drawPath(fill,paint);paint.setShader(null);
            }
        }
        paint.setColor(main);paint.setStrokeWidth(3f);
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(line,paint);
        paint.setColor(TEXT);paint.setTextSize(17f);paint.setStyle(Paint.Style.FILL);
        canvas.drawText(mode==1?"100%":String.format(Locale.US,"%.0f",max)+"ms",2,top+10,paint);
        canvas.drawText("0",21,bottom,paint);
        if(from>0) {
            SimpleDateFormat sdf=new SimpleDateFormat("HH:mm",Locale.getDefault());
            canvas.drawText(sdf.format(new Date(from*1000L)),left,bottom+20,paint);
            String last=sdf.format(new Date(until*1000L));
            canvas.drawText(last,right-paint.measureText(last),bottom+20,paint);
        }
        return result;
    }
}
