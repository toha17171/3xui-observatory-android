package dev.observatory.xui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Main app: live status and full in-app settings; no embedded browser or external libraries. */
public final class MainActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private LinearLayout content;
    private boolean settings=false,newPanel=false;
    private int bg, surface, text, muted, accent, border;

    private int d(float dp){return (int)(getResources().getDisplayMetrics().density*dp+0.5f);}
    private LinearLayout.LayoutParams width(){return new LinearLayout.LayoutParams(-1,-2);}
    private GradientDrawable fill(int color,int rad){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(d(rad));return g;}

    @Override public void onCreate(Bundle savedInstanceState){super.onCreate(savedInstanceState);RefreshJob.schedule(this);render();}
    @Override public void onDestroy(){io.shutdownNow();super.onDestroy();}
    private void palette(){
        boolean dark=Store.dark(this);
        bg=Color.parseColor(dark?"#1A1B1F":"#E6E8EC");surface=Color.parseColor(dark?"#23252B":"#FFFFFF");
        text=Color.parseColor(dark?"#F6F7FA":"#202329");muted=Color.parseColor(dark?"#A4A9B2":"#626A78");
        accent=Color.parseColor("#1677FF");border=Color.parseColor(dark?"#383B43":"#D5D9DF");
    }
    private TextView label(String s,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
        if(bold)t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){LinearLayout c=column();c.setBackground(fill(surface,16));c.setPadding(d(16),d(14),d(16),d(14));
        LinearLayout.LayoutParams p=width();p.setMargins(0,d(8),0,d(8));content.addView(c,p);return c;}
    private void gap(LinearLayout c,int dp){View v=new View(this);c.addView(v,new LinearLayout.LayoutParams(1,d(dp)));}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(14);return b;}
    private void title(LinearLayout c,String s){c.addView(label(s,19,text,true));gap(c,8);}
    private void helper(LinearLayout c,String s){TextView t=label(s,12,muted,false);c.addView(t);}
    private EditText edit(LinearLayout c,String hint,String value,boolean secret){
        TextView caption=label(hint,12,muted,true);c.addView(caption);gap(c,3);
        EditText field=new EditText(this);field.setText(value);field.setTextSize(15);field.setTextColor(text);
        field.setHintTextColor(muted);field.setSingleLine(true);field.setSelectAllOnFocus(false);
        field.setBackgroundTintList(android.content.res.ColorStateList.valueOf(border));
        if(secret)field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if(hint.contains("URL"))field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        c.addView(field,width());gap(c,12);return field;
    }
    private void render(){
        palette();getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(Store.dark(this)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        content=column();content.setPadding(d(18),d(22),d(18),d(28));scroll.addView(content);
        setContentView(scroll);
        content.addView(label("3x-ui Observatory",25,text,true));
        helper(content,"INBOUND / Трафик  •  OUTBOUND / Observatory");gap(content,14);
        LinearLayout tabs=row();content.addView(tabs,width());
        Button overview=button("Обзор");Button config=button("Настройки");
        tabs.addView(overview,new LinearLayout.LayoutParams(0,d(48),1));tabs.addView(config,new LinearLayout.LayoutParams(0,d(48),1));
        overview.setEnabled(settings);config.setEnabled(!settings);
        overview.setOnClickListener(v->{settings=false;newPanel=false;render();});
        config.setOnClickListener(v->{settings=true;newPanel=false;render();});
        gap(content,8);
        if(settings)showSettings();else showOverview();
    }
    private void showOverview(){
        Store.Panel panel=Store.selected(this);
        if(panel==null){
            LinearLayout c=card();title(c,"Добавьте сервер");helper(c,"Нужны адрес HTTPS-панели и API-токен с областью monitor.");
            Button add=button("Перейти к настройкам");c.addView(add);add.setOnClickListener(v->{settings=true;newPanel=true;render();});return;
        }
        List<Store.Panel> panels=Store.panels(this);
        if(panels.size()>1){
            LinearLayout picker=card();picker.addView(label("Сервер",14,muted,true));
            Spinner profiles=new Spinner(this);ArrayList<String> names=new ArrayList<>();int sel=0;
            for(int i=0;i<panels.size();i++){names.add(panels.get(i).name);if(panels.get(i).id.equals(panel.id))sel=i;}
            ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names);profiles.setAdapter(adapter);profiles.setSelection(sel);
            profiles.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                boolean first=true;
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    if(first){first=false;return;}
                    if(!panels.get(position).id.equals(panel.id)){Store.select(MainActivity.this,panels.get(position).id);ObservatoryWidget.updateAll(MainActivity.this);render();}
                }
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });picker.addView(profiles,width());
        }
        LinearLayout head=card();title(head,panel.name);helper(head,panel.url);
        JSONArray data=Store.cached(this,panel.id);long last=Store.lastOk(this,panel.id);
        String err=Store.error(this,panel.id);
        int online=0;List<JSONObject> arr=new ArrayList<>();
        for(int i=0;i<data.length();i++){JSONObject o=data.optJSONObject(i);if(o!=null){arr.add(o);if(o.optBoolean("alive"))online++;}}
        TextView summary=label(online+" / "+arr.size()+" доступны",21,arr.isEmpty()?muted:accent,true);
        head.addView(summary);gap(head,5);
        helper(head,last>0?"Последнее обновление: "+new SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(new Date(last)):"Данные ещё не загружены");
        if(!err.isEmpty()){gap(head,7);TextView error=label("Ошибка: "+err,13,Color.rgb(220,92,92),false);head.addView(error);}
        Button refresh=button("↻   Обновить сейчас");head.addView(refresh);refresh.setOnClickListener(v->{
            refresh.setEnabled(false);refresh.setText("Загружаю…");
            io.execute(()->{String e=Syncer.refreshOne(getApplicationContext(),panel);runOnUiThread(()->{
                if(!isFinishing()){if(e!=null)Toast.makeText(this,e,Toast.LENGTH_LONG).show();render();}
            });});
        });
        LinearLayout nodes=card();title(nodes,"Обсерватория · Outbound");
        List<JSONObject> visible=ObservatorySettings.filtered(data,
                ObservatorySettings.forPanel(this,panel.id));
        if(visible.isEmpty()){
            helper(nodes,"Нет выбранных outbound. Обновите данные или проверьте фильтр в настройках.");
        }
        int healthy=0;
        for(JSONObject o:visible)if(o.optBoolean("alive"))healthy++;
        if(!visible.isEmpty()){
            TextView count=label(healthy+" / "+visible.size()+" активны",14,
                healthy==visible.size()?Color.rgb(82,196,26):Color.rgb(250,173,20),true);
            nodes.addView(count);gap(nodes,10);
        }
        for(JSONObject node:visible){
            LinearLayout r=row();r.setPadding(0,d(6),0,d(6));
            boolean alive=node.optBoolean("alive");
            TextView tag=label("●  "+node.optString("tag","?"),14,
                alive?text:Color.rgb(255,100,105),true);
            r.addView(tag,new LinearLayout.LayoutParams(0,-2,1));
            TextView latency=label(alive?node.optLong("delay")+" мс":"DOWN",13,
                alive?accent:Color.rgb(255,100,105),true);
            r.addView(latency);nodes.addView(r,width());
        }
        if(!visible.isEmpty()){
            gap(nodes,9);
            helper(nodes,"ИСТОРИЯ ЗАДЕРЖКИ · API Xray Observatory");
            final List<String> names=new ArrayList<>();
            for(JSONObject o:visible)names.add(o.optString("tag"));
            Spinner metric=new Spinner(this);
            metric.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,names));
            nodes.addView(metric,width());
            ImageView history=new ImageView(this);
            history.setScaleType(ImageView.ScaleType.FIT_XY);
            LinearLayout.LayoutParams params=width();
            params.height=d(148);
            nodes.addView(history,params);
            metric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    if(position<names.size() && !isFinishing())
                        history.setImageBitmap(ObservatoryChart.draw(MainActivity.this,
                            panel.id,names.get(position),0,640,215));
                }
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });
            history.setImageBitmap(ObservatoryChart.draw(this,panel.id,names.get(0),0,640,215));
            gap(nodes,4);
            helper(nodes,"Выбор отображаемых outbound и интервал графика — в настройках.");
        }
        showInboundOverview(panel);
        gap(content,3);helper(content,"Фоновое обновление: каждые "+Store.interval(this)+" мин (приблизительно). Нажатие на виджет открывает приложение.");
    }
    private void showInboundOverview(Store.Panel panel){
        Set<String> selected=DashboardStore.selectedInbounds(this,panel.id);
        List<JSONObject> incoming=DashboardStore.visibleInbounds(this,panel.id,selected);
        LinearLayout c=card();
        title(c,"Inbound · Трафик");
        String error=DashboardStore.inboundError(this,panel.id);
        if(!error.isEmpty()){
            helper(c,"Не удалось загрузить inbound: "+error);gap(c,8);
        }
        if(incoming.isEmpty()){
            helper(c,"Нет данных о входящих подключениях. Нажмите «Обновить» и проверьте доступ токена к inbound API.");
            return;
        }
        long totalUp=0,totalDown=0;
        int enabled=0;
        for(JSONObject r:incoming){
            if(r.optBoolean("enable"))enabled++;
            totalUp+=r.optLong("up");
            totalDown+=r.optLong("down");
        }
        helper(c,enabled+" из "+incoming.size()+" включены · счётчики с момента последнего сброса");
        gap(c,5);
        TextView total=label("↓ "+DashboardStore.formatBytes(totalDown)+"    ↑ "+DashboardStore.formatBytes(totalUp),
            20,text,true);
        c.addView(total);
        gap(c,12);
        c.addView(label("ДИНАМИКА ТРАФИКА  •  ↓ DOWNLOAD / ↑ UPLOAD",11,muted,true));
        ImageView chart=new ImageView(this);
        chart.setImageBitmap(TrafficChart.draw(this,panel.id,selected,640,210));
        chart.setScaleType(ImageView.ScaleType.FIT_XY);
        LinearLayout.LayoutParams chartParams=new LinearLayout.LayoutParams(-1,d(115));
        chartParams.setMargins(0,d(5),0,d(8));
        c.addView(chart,chartParams);
        for(JSONObject in:incoming){
            LinearLayout r=row();r.setPadding(0,d(7),0,d(7));
            boolean active=in.optBoolean("enable");
            String name=in.optString("remark").isEmpty()?
               in.optString("protocol","inbound"):in.optString("remark");
            TextView nameView=label((active?"● ":"○ ")+name+" : "+in.optInt("port"),13,
                active?text:Color.rgb(255,77,79),true);
            r.addView(nameView,new LinearLayout.LayoutParams(0,-2,1));
            TextView bytes=label(DashboardStore.formatBytes(in.optLong("down"))+" ↓",12,muted,false);
            r.addView(bytes);c.addView(r,width());
        }
        gap(c,5);helper(c,"Выбор отображаемых inbound доступен в настройках.");
    }

    private void showSettings(){
        List<Store.Panel> panels=Store.panels(this);
        Store.Panel selected=Store.selected(this);
        LinearLayout pick=card();title(pick,"Профили 3x-ui");
        if(!panels.isEmpty()){
            ArrayList<String> names=new ArrayList<>();int selectedIndex=0;
            for(int i=0;i<panels.size();i++){
                names.add(panels.get(i).name);if(selected!=null&&panels.get(i).id.equals(selected.id))selectedIndex=i;
            }
            Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            spinner.setSelection(selectedIndex);pick.addView(spinner,width());
            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                boolean first=true;
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
                    if(first){first=false;return;}
                    newPanel=false;Store.select(MainActivity.this,panels.get(position).id);render();
                }
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });
        }
        Button add=button("+ Добавить сервер");pick.addView(add);add.setOnClickListener(v->{newPanel=true;render();});
        Store.Panel active=newPanel?null:selected;
        LinearLayout credentials=card();title(credentials,active==null?"Новый сервер":"Подключение к "+active.name);
        EditText name=edit(credentials,"Название сервера",active==null?"":active.name,false);
        EditText url=edit(credentials,"URL HTTPS панели (с базовым путём)",active==null?"":active.url,false);
        EditText token=edit(credentials,active==null?"API-токен monitor":"API-токен monitor (пусто = оставить прежний)","",true);
        helper(credentials,"Токен сохраняется в зашифрованном виде. Системные сертификаты TLS проверяются штатно.");
        gap(credentials,6);
        Button save=button("Сохранить подключение");credentials.addView(save);
        save.setOnClickListener(v->{
            try{
                String trimmedName=name.getText().toString().trim();if(trimmedName.isEmpty())throw new IllegalArgumentException("Введите название сервера");
                String normalized=ObservatoryApi.baseUrl(url.getText().toString());
                String id=active==null?Store.newId():active.id;
                Store.savePanel(this,new Store.Panel(id,trimmedName,normalized),token.getText().toString().trim());
                RefreshJob.schedule(this);ObservatoryWidget.updateAll(this);newPanel=false;
                Toast.makeText(this,"Сервер сохранён",Toast.LENGTH_SHORT).show();render();
            }catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        Button test=button("Проверить подключение");credentials.addView(test);
        test.setOnClickListener(v->{
            String inputUrl=url.getText().toString(),inputToken=token.getText().toString().trim();
            test.setEnabled(false);test.setText("Проверка…");
            io.execute(()->{
                String message;
                try{
                    String value=inputToken;
                    if(value.isEmpty()&&active!=null)value=Store.token(getApplicationContext(),active.id);
                    JSONArray data=ObservatoryApi.fetch(inputUrl,value);
                    message="Подключено, outbound: "+data.length();
                }catch(Exception e){message="Ошибка: "+e.getMessage();}
                final String result=message;
                runOnUiThread(()->{test.setEnabled(true);test.setText("Проверить подключение");
                    new AlertDialog.Builder(this).setTitle("Результат проверки").setMessage(result).setPositiveButton("OK",null).show();});
            });
        });
        if(active!=null){
            Button delete=button("Удалить сервер");credentials.addView(delete);
            delete.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Удалить "+active.name+"?").setMessage("Настройки, токен и кеш этого сервера будут удалены.")
                .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dialog,which)->{
                    Store.removePanel(this,active.id);DashboardStore.removePanel(this,active.id);RefreshJob.schedule(this);ObservatoryWidget.updateAll(this);render();
                }).show());
        }
        if(selected!=null){
            LinearLayout obsFilter=card();
            title(obsFilter,"Observatory · Выбор метрик");
            helper(obsFilter,"Выбери outbound для главного экрана. У каждого виджета — свои настройки.");
            gap(obsFilter,9);
            JSONArray availableObs=Store.cached(this,selected.id);
            Set<String> savedTags=ObservatorySettings.forPanel(this,selected.id);
            List<CheckBox> tags=new ArrayList<>();
            for(int i=0;i<availableObs.length();i++){
                JSONObject entry=availableObs.optJSONObject(i);
                if(entry==null)continue;
                String tag=entry.optString("tag","");
                if(tag.isEmpty())continue;
                CheckBox cb=new CheckBox(this);
                cb.setTag(tag);
                cb.setText((entry.optBoolean("alive")?"●  ":"○  ")+tag);
                cb.setTextColor(text);
                cb.setChecked(savedTags.isEmpty()||savedTags.contains(tag));
                obsFilter.addView(cb);tags.add(cb);
            }
            if(tags.isEmpty())helper(obsFilter,"Список пока пуст. Нажмите «Обновить» на главном экране.");
            gap(obsFilter,8);
            helper(obsFilter,"АГРЕГАЦИЯ ИСТОРИИ");
            Spinner period=new Spinner(this);
            String[] options={"30 секунд","2 минуты","5 минут"};
            period.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
            int currentBucket=ObservatorySettings.bucket(this,selected.id);
            period.setSelection(currentBucket==30?0:currentBucket==300?2:1);
            obsFilter.addView(period,width());
            Button applyObs=button("Сохранить выбор Observatory");
            obsFilter.addView(applyObs);
            applyObs.setOnClickListener(v->{
                Set<String> saved=new HashSet<>();
                for(CheckBox item:tags)if(item.isChecked())saved.add((String)item.getTag());
                if(!tags.isEmpty()&&saved.isEmpty()){
                    Toast.makeText(this,"Выберите хотя бы один outbound",Toast.LENGTH_SHORT).show();
                    return;
                }
                ObservatorySettings.savePanel(this,selected.id,saved);
                int bucket=period.getSelectedItemPosition()==0?30:
                      period.getSelectedItemPosition()==2?300:120;
                ObservatorySettings.bucket(this,selected.id,bucket);
                ObservatoryWidget.updateAll(this);
                Toast.makeText(this,"Настройки Observatory сохранены",Toast.LENGTH_SHORT).show();
                Store.Panel reload=selected;
                io.execute(()->{
                    Syncer.refreshOne(getApplicationContext(),reload);
                    runOnUiThread(()->{if(!isFinishing())render();});
                });
            });

            LinearLayout filter=card();
            title(filter,"Фильтр inbound");
            helper(filter,"Выбери inbound для главного экрана. У каждого виджета свой независимый выбор.");
            gap(filter,10);
            JSONArray available=DashboardStore.inbounds(this,selected.id);
            Set<String> currently=DashboardStore.selectedInbounds(this,selected.id);
            List<CheckBox> toggles=new ArrayList<>();
            for(int i=0;i<available.length();i++){
                JSONObject inbound=available.optJSONObject(i);if(inbound==null)continue;
                String iid=String.valueOf(inbound.optInt("id"));
                CheckBox toggle=new CheckBox(this);
                String display=inbound.optString("remark").isEmpty()?
                   inbound.optString("protocol","inbound"):inbound.optString("remark");
                toggle.setText(display+"  ·  "+inbound.optString("protocol")+" : "+inbound.optInt("port"));
                toggle.setTag(iid);
                toggle.setTextColor(text);
                toggle.setChecked(currently.isEmpty()||currently.contains(iid));
                filter.addView(toggle);
                toggles.add(toggle);
            }
            if(toggles.isEmpty()){
                helper(filter,"Список inbound пуст. Сначала нажмите «Обновить» на вкладке Обзор.");
            }else{
                Button apply=button("Применить выбор inbound");
                filter.addView(apply);
                apply.setOnClickListener(v->{
                    Set<String> ids=new HashSet<>();
                    for(CheckBox toggle:toggles)if(toggle.isChecked())ids.add((String)toggle.getTag());
                    if(ids.isEmpty()){
                        Toast.makeText(this,"Выберите хотя бы один inbound",Toast.LENGTH_LONG).show();
                        return;
                    }
                    DashboardStore.selectInbounds(this,selected.id,ids);
                    ObservatoryWidget.updateAll(this);
                    Toast.makeText(this,"Фильтр сохранён",Toast.LENGTH_SHORT).show();
                });
            }
        }
        LinearLayout common=card();title(common,"Параметры приложения");
        common.addView(label("Интервал обновления",13,muted,true));
        int[] minutes={15,30,60};String[] choices={"15 минут","30 минут","60 минут"};
        Spinner interval=new Spinner(this);interval.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        int current=Store.interval(this);int index=current==60?2:(current==30?1:0);interval.setSelection(index);common.addView(interval,width());
        Switch darkToggle=new Switch(this);darkToggle.setText("Тёмная тема приложения");darkToggle.setTextColor(text);darkToggle.setChecked(Store.dark(this));common.addView(darkToggle);
        Switch alerts=new Switch(this);alerts.setText("Уведомлять при отказе outbound");alerts.setTextColor(text);alerts.setChecked(Store.notifications(this));common.addView(alerts);
        Switch recover=new Switch(this);recover.setText("Уведомлять при восстановлении");recover.setTextColor(text);recover.setChecked(Store.recovery(this));common.addView(recover);
        Button saveSettings=button("Сохранить параметры");common.addView(saveSettings);
        helper(common,"Android запускает периодические проверки по своему расписанию; 15 минут — минимальный интервал.");
        saveSettings.setOnClickListener(v->{
            Store.interval(this,minutes[interval.getSelectedItemPosition()]);Store.dark(this,darkToggle.isChecked());
            Store.notifications(this,alerts.isChecked());Store.recovery(this,recover.isChecked());
            RefreshJob.schedule(this);ObservatoryWidget.updateAll(this);
            if(alerts.isChecked()&&Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},77);
            Toast.makeText(this,"Настройки сохранены",Toast.LENGTH_SHORT).show();render();
        });
    }
}
