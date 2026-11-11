package dev.observatory.xui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Configure each widget's selected inbounds, selected outbounds, and graph. */
public final class WidgetConfigureActivity extends Activity {
    private static final int BG=Color.rgb(26,27,31);
    private static final int CARD=Color.rgb(35,37,43);
    private static final int TEXT=Color.rgb(243,245,248);
    private static final int MUTED=Color.rgb(164,172,186);
    private static final int BLUE=Color.rgb(22,119,255);
    private int widgetId=AppWidgetManager.INVALID_APPWIDGET_ID;
    private LinearLayout screen,inboundBox,outboundBox;
    private Spinner panelSpinner,chartSpinner,focusSpinner,bucketSpinner;
    private CheckBox probeSwitch,trafficSwitch;
    private List<Store.Panel> panels;
    private final List<CheckBox> inboundChecks=new ArrayList<>();
    private final List<CheckBox> outboundChecks=new ArrayList<>();
    private final List<String> focusTags=new ArrayList<>();
    private String panelId="";
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private TextView text(String content,int size,boolean bold,int color) {
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);
        return t;
    }
    private LinearLayout section(String heading,String detail) {
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(CARD,14));c.setPadding(dp(16),dp(15),dp(16),dp(16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,dp(8));screen.addView(c,p);
        c.addView(text(heading,18,true,TEXT));
        if(!detail.isEmpty()){
            TextView sub=text(detail,12,false,MUTED);sub.setPadding(0,dp(5),0,dp(12));
            c.addView(sub);
        }
        return c;
    }
    private Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);
        b.setTextColor(TEXT);b.setBackgroundTintList(ColorStateList.valueOf(0xFF2257A2));
        return b;
    }
    private CheckBox check(String label,boolean enabled,String id) {
        CheckBox b=new CheckBox(this);b.setText(label);b.setTextSize(13);b.setTextColor(TEXT);
        b.setChecked(enabled);b.setTag(id);
        b.setPadding(0,dp(4),0,dp(4));
        b.setButtonTintList(ColorStateList.valueOf(BLUE));return b;
    }
    private Spinner spinner(String[] values) {
        Spinner s=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,values);
        s.setAdapter(adapter);return s;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        widgetId=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                           AppWidgetManager.INVALID_APPWIDGET_ID);
        if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        panels=Store.panels(this);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18),dp(22),dp(18),dp(24));scroll.addView(screen);setContentView(scroll);
        screen.addView(text("3x-ui  /  Виджет",24,true,TEXT));
        TextView hint=text("Выбери показатели для этого виджета.",13,false,MUTED);
        hint.setPadding(0,dp(5),0,dp(14));screen.addView(hint);
        if(panels.isEmpty()){
            LinearLayout card=section("Сервер не настроен","Добавь HTTPS-адрес панели и токен в основном приложении.");
            Button open=button("Открыть настройки");
            card.addView(open);open.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
            return;
        }
        LinearLayout server=section("Панель 3x-ui","Каждый виджет может следить за своим сервером.");
        List<String> panelNames=new ArrayList<>();
        int index=0;String saved=DashboardStore.panelForWidget(this,widgetId);
        for(int i=0;i<panels.size();i++){
            panelNames.add(panels.get(i).name);
            if(panels.get(i).id.equals(saved)) index=i;
        }
        panelSpinner=spinner(panelNames.toArray(new String[0]));server.addView(panelSpinner);
        LinearLayout outbound=section("Observatory · Outbound",
            "Выбранные соединения, доступность, задержка и история измерений.");
        outboundBox=new LinearLayout(this);outboundBox.setOrientation(LinearLayout.VERTICAL);
        outbound.addView(outboundBox);
        LinearLayout inbound=section("Inbound · Трафик",
            "Суммарный трафик и статусы выбранных входящих подключений.");
        inboundBox=new LinearLayout(this);inboundBox.setOrientation(LinearLayout.VERTICAL);
        inbound.addView(inboundBox);
        LinearLayout graph=section("График и показатели","Точки истории берутся из API Observatory.");
        graph.addView(text("ТИП ГРАФИКА",12,true,MUTED));
        chartSpinner=spinner(new String[]{
            "Задержка outbound · мс","Доступность outbound · %","Скорость inbound · Б/с"});
        graph.addView(chartSpinner);
        chartSpinner.setSelection(ObservatorySettings.chart(this,widgetId));
        graph.addView(text("OUTBOUND ДЛЯ ГРАФИКА",12,true,MUTED));
        focusSpinner=spinner(new String[]{"Загрузите outbound"});
        graph.addView(focusSpinner);
        graph.addView(text("ИНТЕРВАЛ ТОЧЕК ИСТОРИИ",12,true,MUTED));
        bucketSpinner=spinner(new String[]{"30 секунд","2 минуты","5 минут"});
        graph.addView(bucketSpinner);
        probeSwitch=check("Показывать время последней проверки",
            ObservatorySettings.showProbe(this,widgetId),"probe");
        graph.addView(probeSwitch);
        trafficSwitch=check("Показывать трафик inbound",
            ObservatorySettings.showTraffic(this,widgetId),"traffic");
        graph.addView(trafficSwitch);
        Button reload=button("↻ Обновить данные с сервера");screen.addView(reload);
        reload.setOnClickListener(v->{
            Store.Panel p=DashboardStore.getPanel(this,panelId);
            if(p==null)return;
            reload.setEnabled(false);
            new Thread(()->{
                String error=Syncer.refreshOne(getApplicationContext(),p);
                runOnUiThread(()->{
                    if(isFinishing())return;
                    reload.setEnabled(true);showForPanel(p.id);
                    if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();
                });
            },"3xui-widget-settings-refresh").start();
        });
        Button save=button("✓ Сохранить виджет");screen.addView(save);
        save.setOnClickListener(v->save());
        final int selected=index;
        panelSpinner.setSelection(selected);
        panelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long row){
                String id=panels.get(position).id;
                if(!id.equals(panelId))showForPanel(id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        showForPanel(panels.get(selected).id);
    }
    private void showForPanel(String id){
        panelId=id;
        outboundBox.removeAllViews();inboundBox.removeAllViews();
        inboundChecks.clear();outboundChecks.clear();focusTags.clear();
        JSONArray snapshot=Store.cached(this,id);
        Set<String> selected=id.equals(DashboardStore.panelForWidget(this,widgetId))
            ?ObservatorySettings.forWidget(this,widgetId,id)
            :ObservatorySettings.forPanel(this,id);
        if(snapshot.length()==0)outboundBox.addView(text("Список пуст — нажми «Обновить данные».",12,false,MUTED));
        for(int i=0;i<snapshot.length();i++){
            JSONObject obj=snapshot.optJSONObject(i);if(obj==null)continue;
            String tag=obj.optString("tag","");
            if(tag.isEmpty())continue;
            focusTags.add(tag);
            CheckBox b=check((obj.optBoolean("alive")?"● ":"○ ")+tag,
                selected.isEmpty()||selected.contains(tag),tag);
            outboundBox.addView(b);outboundChecks.add(b);
        }
        JSONArray inbound=DashboardStore.inbounds(this,id);
        Set<String> chosen=id.equals(DashboardStore.panelForWidget(this,widgetId))
            ?DashboardStore.selectionForWidget(this,widgetId,id)
            :DashboardStore.selectedInbounds(this,id);
        if(inbound.length()==0)inboundBox.addView(text("Нет данных inbound.",12,false,MUTED));
        for(int i=0;i<inbound.length();i++){
            JSONObject row=inbound.optJSONObject(i);if(row==null)continue;
            String key=String.valueOf(row.optInt("id"));
            String name=row.optString("remark","");
            if(name.isEmpty())name=row.optString("protocol","inbound");
            CheckBox b=check(name+" · :"+row.optInt("port"),
                chosen.isEmpty()||chosen.contains(key),key);
            inboundBox.addView(b);inboundChecks.add(b);
        }
        if(focusTags.isEmpty())focusSpinner.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,new String[]{"Нет данных"}));
        else {
            focusSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,focusTags));
            int index=focusTags.indexOf(ObservatorySettings.focus(this,widgetId));
            focusSpinner.setSelection(Math.max(0,index));
        }
        int b=ObservatorySettings.bucket(this,id);
        bucketSpinner.setSelection(b==30?0:b==300?2:1);
    }
    private void save(){
        Set<String> in=new LinkedHashSet<>(),out=new LinkedHashSet<>();
        for(CheckBox b:inboundChecks)if(b.isChecked())in.add(String.valueOf(b.getTag()));
        for(CheckBox b:outboundChecks)if(b.isChecked())out.add(String.valueOf(b.getTag()));
        if(!inboundChecks.isEmpty()&&in.isEmpty()){
            Toast.makeText(this,"Отметь хотя бы один inbound",Toast.LENGTH_SHORT).show();return;
        }
        if(!outboundChecks.isEmpty()&&out.isEmpty()){
            Toast.makeText(this,"Отметь хотя бы один outbound",Toast.LENGTH_SHORT).show();return;
        }
        DashboardStore.configureWidget(this,widgetId,panelId,in);
        String focus=focusTags.isEmpty()?"":focusTags.get(Math.min(
            Math.max(0,focusSpinner.getSelectedItemPosition()),focusTags.size()-1));
        if(!out.isEmpty()&&!out.contains(focus))focus=out.iterator().next();
        ObservatorySettings.saveWidget(this,widgetId,out,chartSpinner.getSelectedItemPosition(),
            focus,probeSwitch.isChecked(),trafficSwitch.isChecked());
        int bucket=bucketSpinner.getSelectedItemPosition()==0?30:
            bucketSpinner.getSelectedItemPosition()==2?300:120;
        ObservatorySettings.bucket(this,panelId,bucket);
        WidgetRenderer.update(this,widgetId);
        Intent result=new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId);
        setResult(RESULT_OK,result);
        Store.Panel panel=DashboardStore.getPanel(this,panelId);
        if(panel!=null)new Thread(()->Syncer.refreshOne(getApplicationContext(),panel),
            "3xui-history-fetch").start();
        finish();
    }
}
