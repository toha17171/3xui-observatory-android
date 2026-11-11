package dev.observatory.xui;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** One read-only HTTPS call to the official 3x-ui API. No certificate bypasses. */
public final class ObservatoryApi {
    private ObservatoryApi() {}
    public static String baseUrl(String raw) {
        try {
            URI u=new URI(raw.trim());
            if(!"https".equalsIgnoreCase(u.getScheme()) || u.getHost()==null ||
                u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)
                throw new IllegalArgumentException("Требуется HTTPS-адрес панели без параметров");
            String path=u.getRawPath()==null?"/":u.getRawPath();
            if(!path.endsWith("/")) path += "/";
            if (path.contains("..")) throw new IllegalArgumentException("Недопустимый путь");
            // URI.resolve needs a trailing slash to preserve a custom 3x-ui web base path.
            return new URI(u.getScheme(),null,u.getHost(),u.getPort(),path,null,null).toASCIIString();
        } catch (IllegalArgumentException e) {throw e;}
        catch(Exception e) {throw new IllegalArgumentException("Некорректный адрес панели",e);}
    }
    public static JSONArray fetch(String base,String token) throws Exception {
        if(token==null||token.trim().isEmpty()) throw new IllegalArgumentException("API-токен не задан");
        URL url=URI.create(baseUrl(base)).resolve("panel/api/server/xrayObservatory").toURL();
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setRequestMethod("GET");conn.setConnectTimeout(10000);conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Authorization","Bearer "+token.trim());
        conn.setRequestProperty("Accept","application/json");
        try {
            int status=conn.getResponseCode();
            if(status==401||status==403) throw new IllegalStateException("HTTP "+status+": проверьте токен и доступ monitor");
            if(status>=300&&status<400) throw new IllegalStateException("HTTP "+status+": перенаправление запрещено");
            if(status!=200) throw new IllegalStateException("HTTP "+status+": сервер не ответил успешно");
            byte[] bytes;
            try(InputStream is=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=is.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>512*1024) throw new IllegalStateException("Слишком большой ответ API");}
                bytes=out.toByteArray();
            }
            JSONObject resp=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!resp.optBoolean("success",false)) throw new IllegalStateException("3x-ui: "+resp.optString("msg","ошибка API"));
            JSONArray arr=resp.optJSONArray("obj");
            if(arr==null) throw new IllegalStateException("Некорректный ответ Observatory (obj не массив)");
            return arr;
        } finally {conn.disconnect();}
    }
}
