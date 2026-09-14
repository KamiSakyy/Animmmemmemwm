package app.yoru.mobile;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.*;
import java.util.*;

public final class YoruWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context,AppWidgetManager manager,int[] ids){for(int id:ids)update(context,manager,id);}
    static void refresh(Context context){try{AppWidgetManager manager=AppWidgetManager.getInstance(context);int[] ids=manager.getAppWidgetIds(new ComponentName(context,YoruWidgetProvider.class));for(int id:ids)update(context,manager,id);}catch(Exception ignored){}}
    private static void update(Context context,AppWidgetManager manager,int id){RemoteViews v=new RemoteViews(context.getPackageName(),R.layout.widget_yoru);SecureStore store=new SecureStore(context);List<Anime> recent=store.recent();Anime a=recent.isEmpty()?null:recent.get(0);v.setTextViewText(R.id.widget_title,a==null?"YORU":"Продолжить");Intent open;
        if(a==null){v.setTextViewText(R.id.widget_text,"Откройте каталог и выберите аниме");open=new Intent(context,MainActivity.class);}
        else{
            org.json.JSONObject progress=store.progress(a);double episode=progress.optDouble("episode",1);String mode=progress.optString("playerMode","auto");
            v.setTextViewText(R.id.widget_text,"Серия "+Ui.number(episode)+" · "+YoruBrain.title(a));
            open=new Intent(context,PlayerActivity.class).putExtra("anime",a.json().toString()).putExtra("mode",mode).putExtra("episode",episode);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(context,9001,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widget_root,pi);manager.updateAppWidget(id,v);}
}
