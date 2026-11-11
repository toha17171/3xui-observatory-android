package dev.observatory.xui;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Widget-specific panel and inbound selection, launched by Android widget picker or gear button. */
public final class WidgetConfigureActivity extends Activity {
    private int id=AppWidgetManager.INVALID_APPWIDGET_ID;
    private LinearLayout root,choices;
    private Spinner panelPicker;
    private List<Store.Panel> panels;
    private Set<String> selection=new HashSet<>();
    private final List<CheckBox> checks=new ArrayList<>();
    private String currentPanelId="";
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return;}
        panels=Store.panels(this);
        ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Color.rgb(26,27,31));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(30),dp(22),dp(24));scroll.addView(root);setContentView(scroll);
        root.addView(text("3x-ui · Настройка виджета",23,Color.WHITE));
        TextView hint=text("Выбери сервер и inbound, которые должны отображаться в этом виджете.",14,Color.rgb(160,169,184));
        hint.setPadding(0,dp(12),0,dp(22));root.addView(hint);
        if(panels.isEmpty()){
            root.addView(text("Сначала добавьте сервер в основном приложении.",15,Color.rgb(255,160,120)));
            Button open=new Button(this);open.setText("Открыть приложение");root.addView(open);
            open.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class)));
            return;
        }
        root.addView(text("СЕРВЕР",12,Color.rgb(160,169,184)));
        panelPicker=new Spinner(this);
        ArrayList<String> labels=new ArrayList<>();
        int chosen=0;String saved=DashboardStore.panelForWidget(this,id);
        for(int i=0;i<panels.size();i++){
            labels.add(panels.get(i).name);if(panels.get(i).id.equals(saved))chosen=i;
        }
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels);
        panelPicker.setAdapter(adapter);root.addView(panelPicker);
        TextView caption=text("INBOUND ДЛЯ ОТОБРАЖЕНИЯ",12,Color.rgb(160,169,184));
        caption.setPadding(0,dp(20),0,dp(8));root.addView(caption);
        choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);root.addView(choices);
        panelPicker.setSelection(chosen);
        panelPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long row){
                Store.Panel p=panels.get(position);
                if(!p.id.equals(currentPanelId))showChoices(p.id);
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        showChoices(saved.isEmpty()?panels.get(chosen).id:saved);
        Button reload=new Button(this);reload.setText("↻ Обновить список inbound");
        root.addView(reload);reload.setOnClickListener(v->{
            final Store.Panel panel=DashboardStore.getPanel(this,currentPanelId);
            reload.setEnabled(false);
            new Thread(()->{
                String error=Syncer.refreshOne(getApplicationContext(),panel);
                runOnUiThread(()->{reload.setEnabled(true);showChoices(panel.id);
                    if(error!=null)Toast.makeText(this,error,Toast.LENGTH_LONG).show();});
            }).start();
        });
        Button save=new Button(this);save.setText("✓ Сохранить виджет");
        root.addView(save);save.setOnClickListener(v->{
            Set<String> selected=new HashSet<>();
            for(CheckBox b:checks)if(b.isChecked())selected.add(String.valueOf(b.getTag()));
            if(!checks.isEmpty()&&selected.isEmpty()){
                Toast.makeText(this,"Выберите хотя бы один inbound",Toast.LENGTH_LONG).show();return;
            }
            DashboardStore.configureWidget(this,id,currentPanelId,selected);
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
            boolean large=info!=null&&info.provider!=null
                    &&info.provider.getClassName().endsWith(".ExpandedWidget");
            WidgetRenderer.update(this,id,large);
            Intent result=new Intent();result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            setResult(RESULT_OK,result);finish();
        });
    }
    private void showChoices(String panelId){
        currentPanelId=panelId;
        choices.removeAllViews();checks.clear();
        JSONArray array=DashboardStore.inbounds(this,panelId);
        Set<String> saved=DashboardStore.selectionForWidget(this,id,panelId);
        if(array.length()==0){
            TextView empty=text("Нет данных. Нажмите «Обновить список» или сохраните виджет: отобразятся все inbound после первого обновления.",13,Color.rgb(160,169,184));
            empty.setPadding(0,dp(12),0,dp(12));choices.addView(empty);return;
        }
        for(int i=0;i<array.length();i++){
            JSONObject row=array.optJSONObject(i);if(row==null)continue;
            String key=String.valueOf(row.optInt("id"));
            CheckBox box=new CheckBox(this);
            box.setText(row.optString("remark","Inbound")+" · "+row.optString("protocol")+" : "+row.optInt("port"));
            box.setTag(key);box.setTextColor(Color.WHITE);
            box.setChecked(saved.isEmpty()||saved.contains(key));
            box.setPadding(0,dp(6),0,dp(6));choices.addView(box);checks.add(box);
        }
    }
}
