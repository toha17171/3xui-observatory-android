package dev.observatory.xui;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

/** Settings and cached observations; tokens are encrypted using Android Keystore. */
public final class Store {
    private static final String PREFS = "observatory_settings_v1";
    private static final String KEY_ALIAS = "observatory_token_key_v1";
    private Store() {}

    public static class Panel {
        public String id, name, url;
        public Panel(String id, String name, String url) { this.id=id; this.name=name; this.url=url; }
    }
    private static SharedPreferences p(Context c) { return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    public static List<Panel> panels(Context context) {
        ArrayList<Panel> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(context).getString("panels", "[]"));
            for (int i=0; i<a.length(); i++) {
                JSONObject o=a.optJSONObject(i);
                if (o!=null && !o.optString("id").isEmpty())
                    result.add(new Panel(o.optString("id"),o.optString("name","3x-ui"),o.optString("url")));
            }
        } catch(Exception ignored) { /* malformed local settings: empty list */ }
        return result;
    }
    public static Panel selected(Context context) {
        String id=p(context).getString("selected", "");
        List<Panel> list=panels(context);
        for (Panel a:list) if (a.id.equals(id)) return a;
        return list.isEmpty()?null:list.get(0);
    }
    public static void select(Context c,String id) { p(c).edit().putString("selected",id).apply(); }
    public static String newId() { return UUID.randomUUID().toString(); }

    public static void savePanel(Context c, Panel panel, String newToken) throws Exception {
        if (newToken != null && !newToken.isEmpty()) {
            String secret=encrypt(newToken);
            p(c).edit().putString("secret_"+panel.id, secret).commit();
        } else if (!hasToken(c,panel.id)) {
            throw new IllegalArgumentException("Укажите API-токен");
        }
        List<Panel> list=panels(c);
        boolean found=false;
        for (int i=0; i<list.size(); i++) if(list.get(i).id.equals(panel.id)) { list.set(i,panel);found=true;break; }
        if (!found) list.add(panel);
        JSONArray arr=new JSONArray();
        for (Panel value:list) {
            JSONObject o=new JSONObject();o.put("id",value.id);o.put("name",value.name);o.put("url",value.url);arr.put(o);
        }
        p(c).edit().putString("panels",arr.toString()).putString("selected",panel.id).apply();
    }
    public static void removePanel(Context c,String id) {
        List<Panel> list=panels(c);
        JSONArray arr=new JSONArray();
        for (Panel v:list) if(!v.id.equals(id)){
            JSONObject o=new JSONObject();try{o.put("id",v.id);o.put("name",v.name);o.put("url",v.url);}catch(Exception ignored){}arr.put(o);
        }
        p(c).edit().putString("panels",arr.toString()).remove("secret_"+id)
          .remove("data_"+id).remove("last_ok_"+id).remove("error_"+id)
          .putString("selected", arr.length()>0?arr.optJSONObject(0).optString("id"):"").apply();
    }
    public static boolean hasToken(Context c,String id) {return p(c).contains("secret_"+id);}
    public static String token(Context c,String id) throws Exception {
        String s=p(c).getString("secret_"+id, null);
        if(s==null) throw new IllegalArgumentException("API-токен не задан");
        return decrypt(s);
    }
    public static int interval(Context c) {return p(c).getInt("interval",15);}
    public static void interval(Context c,int m) {p(c).edit().putInt("interval",m).apply();}
    public static boolean dark(Context c) {return p(c).getBoolean("dark",true);}
    public static void dark(Context c,boolean b) {p(c).edit().putBoolean("dark",b).apply();}
    public static boolean notifications(Context c) {return p(c).getBoolean("notify",false);}
    public static void notifications(Context c,boolean b) {p(c).edit().putBoolean("notify",b).apply();}
    public static boolean recovery(Context c) {return p(c).getBoolean("recovery",true);}
    public static void recovery(Context c,boolean b) {p(c).edit().putBoolean("recovery",b).apply();}

    public static JSONArray cached(Context c,String id) {
        try {return new JSONArray(p(c).getString("data_"+id,"[]"));}catch(Exception e){return new JSONArray();}
    }
    public static long lastOk(Context c,String id){return p(c).getLong("last_ok_"+id,0);}
    public static String error(Context c,String id){return p(c).getString("error_"+id,"");}
    public static void updateCache(Context c,String id,JSONArray data){
        p(c).edit().putString("data_"+id,data.toString())
          .putLong("last_ok_"+id,System.currentTimeMillis()).remove("error_"+id).commit();
    }
    public static void updateError(Context c,String id,String error){
        p(c).edit().putString("error_"+id,error).apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        KeyStore.Entry e=ks.getEntry(KEY_ALIAS,null);
        if(e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry)e).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256).build());
        return generator.generateKey();
    }
    private static String encrypt(String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
    }
    private static String decrypt(String value) throws Exception {
        String[] p=value.split(":",2);if(p.length!=2) throw new IllegalArgumentException("Повреждённый токен");
        byte[] iv=Base64.decode(p[0],Base64.NO_WRAP);byte[] data=Base64.decode(p[1],Base64.NO_WRAP);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(data),StandardCharsets.UTF_8);
    }
}
