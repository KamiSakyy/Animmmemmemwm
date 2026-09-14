package app.yoru.mobile;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.exoplayer.offline.*;
import org.json.*;
import java.util.*;

public final class DownloadsScreen extends LinearLayout {
    private final Activity activity;private final DownloadHub hub;private final ArrayList<Entry> rows=new ArrayList<>();private final TextView summary,empty;private final ListView list;private final Adapter adapter;private final Handler handler=new Handler(Looper.getMainLooper());private String lastSignature="";
    private final Runnable update=new Runnable(){public void run(){refreshSafe();handler.postDelayed(this,5000);}};
    public DownloadsScreen(Activity a){super(a);activity=a;hub=YoruApp.app().downloads();setOrientation(VERTICAL);setPadding(Ui.dp(a,17),Ui.dp(a,15),Ui.dp(a,17),Ui.dp(a,8));addView(Ui.label(a,"ОФЛАЙН-БИБЛИОТЕКА"));Ui.space(this,10);addView(Ui.text(a,"Загрузки",28,Ui.TEXT,true));Ui.space(this,9);addView(Ui.text(a,"Скачанные серии с превью и быстрым запуском в YORU. В выбранную папку сохраняются видео и звук; отдельные дорожки субтитров остаются в YORU.",12,Ui.MUTED,false));Ui.space(this,10);addView(Ui.button(a,"Папка сохранения",false,()->DocumentDownloads.choose(a)),Ui.lp(a,-1,-2));Ui.space(this,16);summary=Ui.text(a,"",12,Ui.PURPLE,true);addView(summary);Ui.space(this,11);
        HorizontalScrollView actionScroll=new HorizontalScrollView(a);actionScroll.setHorizontalScrollBarEnabled(false);LinearLayout actionRow=Ui.row(a);
        bulkChip(actionRow,"Смотреть подряд",()->YoruApp.app().io.submit(()->{
            ArrayList<Download> ready=new ArrayList<>();
            for(Download d:hub.all())if(d!=null&&d.request!=null&&d.state==Download.STATE_COMPLETED)ready.add(d);
            if(ready.isEmpty()){YoruApp.app().main.post(()->Ui.toast(activity,"Нет скачанных серий"));return;}
            java.util.LinkedHashMap<String,JSONObject> metas=new java.util.LinkedHashMap<>();
            for(Download d:ready)metas.put(d.request.id,DownloadHub.metadata(d));
            ready.sort((x,y)->{
                JSONObject mx=metas.get(x.request.id),my=metas.get(y.request.id);
                Anime ax=Anime.from(mx==null?null:mx.optJSONObject("anime")),ay=Anime.from(my==null?null:my.optJSONObject("anime"));
                int titles=YoruBrain.title(ax).compareToIgnoreCase(YoruBrain.title(ay));
                if(titles!=0)return titles;
                return Double.compare(mx==null?1:mx.optDouble("episode",1),my==null?1:my.optDouble("episode",1));
            });
            Download first=ready.get(0);JSONObject meta=metas.get(first.request.id);
            Anime anime=Anime.from(meta==null?null:meta.optJSONObject("anime"));
            double episode=meta==null?1:meta.optDouble("episode",1);
            YoruApp.app().main.post(()->{if(attached)Ui.openOffline(activity,first.request.id,anime,episode);});
        }));
        bulkChip(actionRow,"Пауза всех",()->{hub.pauseAll();Ui.toast(a,"Все загрузки приостановлены");refreshSafe();});
        bulkChip(actionRow,"Продолжить всех",()->{hub.resumeAll();Ui.toast(a,"Загрузки продолжены");refreshSafe();});
        bulkChip(actionRow,"Повторить неудачные",()->{hub.retryFailed();refreshSafe();});
        bulkChip(actionRow,"Убрать готовые",()->Ui.confirm(a,"Убрать готовые загрузки?","Скачанные файлы будут удалены с устройства. Коллекция и история не изменятся.","Убрать",()->{hub.removeCompleted();refreshSafe();},"Отмена"));
        actionScroll.addView(actionRow);addView(actionScroll,Ui.lp(a,-1,-2));Ui.space(this,10);
        LinearLayout filterRow=Ui.row(a);filterChip(filterRow,"Все","all");filterChip(filterRow,"Скачивается","active");filterChip(filterRow,"Готово","done");filterChip(filterRow,"Запланировано","planned");updateFilterChips();
        addView(filterRow,Ui.lp(a,-1,-2));Ui.space(this,12);list=new ListView(a);list.setDivider(null);list.setClipToPadding(false);list.setVerticalScrollBarEnabled(false);list.setPadding(0,0,0,Ui.dp(a,15));list.setBackgroundColor(Ui.BG);adapter=new Adapter();list.setAdapter(adapter);empty=Ui.text(a,"Здесь появятся скачанные серии.\n\nОткройте аниме и нажмите «Скачать» рядом с нужной серией. Готовые загрузки открываются прямо в YORU.",14,Ui.MUTED,false);empty.setPadding(Ui.dp(a,20),Ui.dp(a,40),Ui.dp(a,20),Ui.dp(a,20));empty.setGravity(Gravity.CENTER);FrameLayout frame=new FrameLayout(a);frame.addView(list,new FrameLayout.LayoutParams(-1,-1));frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));addView(frame,new LinearLayout.LayoutParams(-1,0,1));list.setOnItemClickListener((parent,v,pos,id)->{try{if(pos>=0&&pos<rows.size()){Download d=rows.get(pos).download;if(d!=null&&d.state==Download.STATE_COMPLETED){JSONObject m=DownloadHub.metadata(d);Ui.openOffline(activity,d.request.id,Anime.from(m.optJSONObject("anime")),m.optDouble("episode",1));}}}catch(Throwable ignored){}});}
    private void filterChip(LinearLayout row,String title,String value){
        TextView chip=Ui.chip(activity,title,false,()->{if(listFilter.equals(value))return;listFilter=value;updateFilterChips();refreshSafe();});
        chip.setTag(value);filterChips.add(chip);LinearLayout.LayoutParams p=Ui.lp(activity,-2,-2);p.rightMargin=Ui.dp(activity,7);row.addView(chip,p);
    }
    private void updateFilterChips(){for(TextView chip:filterChips){boolean on=listFilter.equals(chip.getTag());
        chip.setTextColor(on?0xff21152f:Ui.MUTED);chip.setBackground(on?Ui.gradient(0xffe2ccff,Ui.PURPLE,13,activity):Ui.stroke(Ui.SURFACE,13,activity));}}
    private void bulkChip(LinearLayout row,String title,Runnable action){
        TextView chip=Ui.chip(activity,title,false,action);LinearLayout.LayoutParams p=Ui.lp(activity,-2,-2);p.rightMargin=Ui.dp(activity,7);row.addView(chip,p);
    }
    private String listFilter="all";private final ArrayList<TextView> filterChips=new ArrayList<>();
    private static final class Entry {
        final Download download;
        final ScheduledDownloads.Plan plan;
        Entry(Download value){download=value;plan=null;}
        Entry(ScheduledDownloads.Plan value){download=null;plan=value;}
    }
    private List<androidx.work.WorkInfo> exportStates=Collections.emptyList();
    private androidx.lifecycle.LiveData<List<androidx.work.WorkInfo>> exportJobs;
    private final androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> exportObserver=this::exportsChanged;
    private boolean attached,refreshing;
    private int generation;
    private java.util.concurrent.Future<?> refreshFuture;

    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;YoruApp.app().io.submit(()->{AdaptiveVideoExport.removeFinishedDrafts(activity);DocumentDownloads.removeFinishedDocuments(activity);});startObserving();}
    @Override protected void onDetachedFromWindow(){attached=false;stopObserving();exportStates=Collections.emptyList();super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(list==null)return;if(visibility==VISIBLE)startObserving();else stopObserving();}
    private void startObserving(){
        if(!attached||getWindowVisibility()!=VISIBLE)return;
        if(exportJobs==null){exportJobs=androidx.work.WorkManager.getInstance(activity).getWorkInfosByTagLiveData(DocumentDownloads.EXPORT_TAG);exportJobs.observeForever(exportObserver);}
        handler.removeCallbacks(update);handler.post(update);
    }
    private void stopObserving(){
        if(exportJobs!=null)exportJobs.removeObserver(exportObserver);exportJobs=null;generation++;refreshing=false;
        if(refreshFuture!=null)refreshFuture.cancel(true);handler.removeCallbacksAndMessages(null);
    }
    private void exportsChanged(List<androidx.work.WorkInfo> jobs){
        if(!attached||getWindowVisibility()!=VISIBLE)return;exportStates=jobs==null?Collections.emptyList():jobs;
        refreshVisibleRows();
    }
    private void refreshVisibleRows(){
        for(int i=0;i<list.getChildCount();i++){
            View view=list.getChildAt(i);int position=list.getFirstVisiblePosition()+i;
            if(!(view.getTag() instanceof DownloadRow)||position<0||position>=rows.size())continue;
            DownloadRow holder=(DownloadRow)view.getTag();Entry entry=rows.get(position);
            if(entry.plan!=null)holder.bindPlan(entry.plan);else holder.bind(entry.download);
        }
    }
    private void refreshSafe(){if(!attached||getWindowVisibility()!=VISIBLE||refreshing)return;refreshing=true;int gen=generation;
        refreshFuture=YoruApp.app().io.submit(()->{
            try {
                ArrayList<Download> downloads=new ArrayList<>(hub.all());
                List<ScheduledDownloads.Plan> plans;
                try(ScheduledDownloads store=new ScheduledDownloads(activity)){plans=store.all();}
                ArrayList<Entry> fresh=new ArrayList<>();HashSet<String> ids=new HashSet<>();
                StringBuilder signature=new StringBuilder();int complete=0,active=0,pending=0;
                for(Download d:downloads){if(d==null||d.request==null)continue;ids.add(d.request.id);fresh.add(new Entry(d));if(d.state==Download.STATE_COMPLETED)complete++;if(d.state==Download.STATE_DOWNLOADING)active++;signature.append(d.request.id).append(':').append(d.state).append(':').append((int)d.getPercentDownloaded()/8).append('|');}
                for(ScheduledDownloads.Plan plan:plans){if(!DownloadListRules.showPlan("queued".equals(plan.state),ids.contains(plan.downloadId())))continue;fresh.add(new Entry(plan));pending++;signature.append(plan.id).append(':').append(plan.state).append(':').append(plan.message).append(':').append(plan.due).append(':').append(plan.dateLabel).append(':').append(plan.voice).append(':').append(plan.quality).append('|');}
                String text="Готово: "+complete+" · скачивается: "+active+" · запланировано: "+pending+" · "+Ui.bytes(YoruApp.app().mediaCache.offlineBytes());
                if(!"all".equals(listFilter)){java.util.Iterator<Entry> it=fresh.iterator();while(it.hasNext()){Entry e=it.next();
                    if(e.plan!=null){if(!"planned".equals(listFilter))it.remove();continue;}
                    if(e.download==null){it.remove();continue;}
                    int st=e.download.state;boolean finished=st==Download.STATE_COMPLETED;
                    boolean running=st==Download.STATE_DOWNLOADING||st==Download.STATE_QUEUED||st==Download.STATE_RESTARTING||st==Download.STATE_STOPPED;
                    boolean keep=("done".equals(listFilter)&&finished)||("active".equals(listFilter)&&running);
                    if(!keep)it.remove();
                }}
                signature.append("#").append(listFilter);
                String next=signature.toString();
                YoruApp.app().main.post(()->{if(!attached||gen!=generation)return;refreshing=false;summary.setText(text);empty.setVisibility(fresh.isEmpty()?VISIBLE:GONE);boolean changed=!next.equals(lastSignature);lastSignature=next;rows.clear();rows.addAll(fresh);if(changed)adapter.notifyDataSetChanged();else refreshVisibleRows();});
            } catch(Exception e){YoruApp.app().main.post(()->{if(!attached||gen!=generation)return;refreshing=false;summary.setText("Не удалось обновить загрузки. Повторим проверку.");});}
        });
        if(refreshFuture.isCancelled()){refreshing=false;summary.setText("Очередь занята. Повторим обновление.");}
    }
    private void cancelPlan(ScheduledDownloads.Plan plan){
        Ui.confirm(activity,"Отменить будущее скачивание?",plan.label(),"Отменить скачивание",()->YoruApp.app().io.execute(()->{
            try(ScheduledDownloads store=new ScheduledDownloads(activity)){store.remove(plan);ScheduledDownloads.schedule(activity);}
            catch(Exception e){YoruApp.app().main.post(()->{if(attached)Ui.toast(activity,"Не удалось отменить задание");});return;}
            YoruApp.app().main.post(()->{if(attached){lastSignature="";refreshSafe();}});
        }),"Назад");
    }
    private void actions(Download d){if(d==null||d.request==null){Ui.toast(activity,"Эта запись загрузки повреждена");return;}JSONObject meta=DownloadHub.metadata(d);Anime a=Anime.from(meta.optJSONObject("anime"));String voice=meta.optString("voice","");String title=(a.title==null||a.title.isEmpty()?"Загрузка":a.title)+" · серия "+Ui.number(meta.optDouble("episode",1))+(voice.isEmpty()?"":" · "+voice);ArrayList<String> choices=new ArrayList<>();if(d.state==Download.STATE_COMPLETED){choices.add("Смотреть в YORU");if(ExportPresentation.active(exportStates,d))choices.add("Остановить сохранение");else if(DocumentDownloads.canExport(d))choices.add("В выбранную папку");if(OfflineExporter.canExport(d)){choices.add("Сохранить через проводник");choices.add("Сохранить в Видео / YORU");choices.add("Поделиться файлом");}choices.add("Информация");}else if(d.state==Download.STATE_STOPPED){choices.add("Продолжить");choices.add("Обновить и повторить");}else if(d.state==Download.STATE_FAILED)choices.add("Обновить и повторить");else if(d.state!=Download.STATE_REMOVING)choices.add("Пауза");choices.add("Удалить загрузку");Ui.choices(activity,title,choices.toArray(new String[0]),which->handle(d,a,meta,choices.get(which)));}
    private void handle(Download d,Anime a,JSONObject meta,String choice){try{if(choice.equals("Смотреть в YORU"))Ui.openOffline(activity,d.request.id,a,meta.optDouble("episode",1));else if(choice.equals("Остановить сохранение"))DocumentDownloads.cancel(activity,d.request.id);else if(choice.equals("В выбранную папку"))DocumentDownloads.save(activity,d,true);else if(choice.equals("Сохранить через проводник"))OfflineExporter.saveWithPicker(activity,d);else if(choice.equals("Сохранить в Видео / YORU"))OfflineExporter.saveToMovies(activity,d);else if(choice.equals("Поделиться файлом"))OfflineExporter.share(activity,d);else if(choice.equals("Информация"))Ui.message(activity,"Информация",OfflineExporter.details(d));else if(choice.equals("Продолжить"))hub.resume(d.request.id);else if(choice.equals("Пауза"))hub.pause(d.request.id);else if(choice.equals("Обновить и повторить"))hub.retry(d.request.id);else confirmDelete(d);}catch(Throwable e){Ui.toast(activity,"Действие загрузки не выполнено");}}
    private void confirmDelete(Download d){Ui.confirm(activity,"Удалить загрузку?","Удалить скачанные файлы этой серии?", "Удалить",()->{DocumentDownloads.cancel(activity,d.request.id);hub.remove(d.request.id);refreshSafe();},"Отмена");}
    private final class Adapter extends BaseAdapter {
        public int getCount(){return rows.size();}
        public Object getItem(int position){return rows.get(position);}
        public long getItemId(int position){return position;}
        public View getView(int position,View old,ViewGroup parent) {
            DownloadRow holder;
            if(old!=null&&old.getTag() instanceof DownloadRow)holder=(DownloadRow)old.getTag();
            else holder=new DownloadRow();
            Entry entry=rows.get(position);if(entry.plan!=null)holder.bindPlan(entry.plan);else holder.bind(entry.download);return holder.outer;
        }
    }

    private final class DownloadRow {
        final LinearLayout outer,card;
        final ImageView poster;
        final FrameLayout preview;
        final TextView placeholder,caption,title,description,type,state,size,primary,save;
        final ProgressBar progress;
        Download download;
        ScheduledDownloads.Plan plan;
        Anime anime;
        JSONObject metadata;
        String imageKey="";
        DownloadRow() {
            outer=Ui.column(activity);outer.setTag(this);outer.setPadding(0,0,0,Ui.dp(activity,10));
            card=Ui.column(activity);card.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));
            card.setBackground(Ui.stroke(Ui.CARD,16,activity));outer.addView(card,Ui.lp(activity,-1,-2));
            preview=new FrameLayout(activity);preview.setBackground(Ui.gradient(0xff2a2135,0xff18131f,18,activity));preview.setClipToOutline(true);
            poster=new ImageView(activity);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.addView(poster,new FrameLayout.LayoutParams(-1,-1));
            placeholder=Ui.text(activity,"",24,Ui.PURPLE,true);placeholder.setGravity(Gravity.CENTER);preview.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            View shade=new View(activity);shade.setBackground(Ui.gradient(0x00111111,0xdd110d18,18,activity));preview.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            caption=Ui.text(activity,"",17,Ui.TEXT,true);caption.setPadding(Ui.dp(activity,14),0,Ui.dp(activity,14),Ui.dp(activity,12));
            preview.addView(caption,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));card.addView(preview,Ui.lp(activity,-1,154));
            Ui.space(card,12);title=Ui.text(activity,"",14,Ui.TEXT,true);title.setMaxLines(2);card.addView(title);
            Ui.space(card,7);description=Ui.text(activity,"",11,Ui.PURPLE,false);card.addView(description);
            Ui.space(card,7);type=Ui.text(activity,"",10,Ui.MUTED,false);card.addView(type);
            Ui.space(card,9);state=Ui.text(activity,"",11,Ui.MUTED,false);card.addView(state);
            progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
            LinearLayout.LayoutParams pp=Ui.lp(activity,-1,7);pp.topMargin=Ui.dp(activity,11);pp.bottomMargin=Ui.dp(activity,9);card.addView(progress,pp);
            size=Ui.text(activity,"",10,Ui.MUTED,false);card.addView(size);Ui.space(card,11);
            LinearLayout controls=Ui.row(activity);
            primary=Ui.button(activity,"",false,this::primaryAction);controls.addView(primary,new LinearLayout.LayoutParams(0,-2,1));
            save=Ui.button(activity,"Сохранить",false,()->{if(download!=null){if(ExportPresentation.active(exportStates,download))DocumentDownloads.cancel(activity,download.request.id);else DocumentDownloads.save(activity,download,true);}});
            controls.addView(save,new LinearLayout.LayoutParams(0,-2,1));
            controls.addView(Ui.iconButton(activity,"trash","Удалить загрузку",()->{if(plan!=null)cancelPlan(plan);else if(download!=null)confirmDelete(download);}),Ui.lp(activity,48,48));
            card.addView(controls);
            preview.setOnClickListener(v->{if(download!=null&&download.state==Download.STATE_COMPLETED)Ui.openOffline(activity,download.request.id,anime,metadata.optDouble("episode",1));});
            card.setOnLongClickListener(v->{if(plan!=null)cancelPlan(plan);else if(download!=null)actions(download);return true;});
        }
        void primaryAction() {
            if(download==null)return;
            if(hub.preparing(download.request.id)){hub.pause(download.request.id);refreshSafe();return;}
            if(download.state==Download.STATE_COMPLETED)Ui.openOffline(activity,download.request.id,anime,metadata.optDouble("episode",1));
            else if(download.state==Download.STATE_STOPPED)hub.resume(download.request.id);
            else if(download.state==Download.STATE_FAILED)hub.retry(download.request.id);
            else hub.pause(download.request.id);
            refreshSafe();
        }
        void bindPlan(ScheduledDownloads.Plan value) {
            plan=value;download=null;anime=value.anime;metadata=null;
            title.setText(YoruBrain.title(anime));
            description.setText("Серия "+Ui.number(value.episode)+" · "+QualityPlus.name(value.quality)+" · "+(value.voice.isEmpty()?"Любая доступная озвучка":value.voice));
            caption.setText("Будущее скачивание");type.setText("Скачать после выхода серии");
            java.time.ZoneId zone=ScheduleClock.zone(YoruApp.app().store.localScheduleTime());String date=value.due>0?ScheduleClock.format(value.due,"dd.MM.yyyy",zone)+" · "+ScheduleClock.time(value.due,zone):value.dateLabel==null||value.dateLabel.isEmpty()?"Дата выхода уточняется":value.dateLabel;
            state.setText(date+"\n"+value.message);state.setTextColor(Ui.MUTED);
            progress.setVisibility(View.GONE);size.setVisibility(View.GONE);primary.setVisibility(View.GONE);save.setVisibility(View.GONE);
            String url=ApiRepository.safeUrl(anime.poster),key="plan:"+value.id+"|"+url;
            poster.setVisibility(url.isEmpty()?View.GONE:View.VISIBLE);placeholder.setVisibility(url.isEmpty()?View.VISIBLE:View.GONE);placeholder.setText("Серия\n"+Ui.number(value.episode));
            if(!key.equals(imageKey)){imageKey=key;poster.setTag(null);poster.setImageDrawable(null);if(!url.isEmpty())YoruApp.app().images.load(poster,url,key);}
        }
        void bind(Download d) {
            plan=null;primary.setVisibility(View.VISIBLE);size.setVisibility(View.VISIBLE);download=d;metadata=DownloadHub.metadata(d);anime=Anime.from(metadata.optJSONObject("anime"));
            title.setText(Anime.valid(anime)?YoruBrain.title(anime):"Загрузка");
            double episode=metadata.optDouble("episode",1);int quality=metadata.optInt("quality");String voice=metadata.optString("voice","");
            description.setText("Серия "+Ui.number(episode)+" · "+QualityPlus.name(quality)+(voice.isEmpty()?"":" · "+voice));
            caption.setText((d.state==Download.STATE_COMPLETED?"▶ ":"")+"Серия "+Ui.number(episode)+(d.state==Download.STATE_COMPLETED?" · готово офлайн":" · загрузка в YORU"));
            String url=ApiRepository.safeUrl(metadata.optString("episodePoster",metadata.optString("poster","")));
            if(url.isEmpty()&&Anime.valid(anime)&&!anime.screenshots.isEmpty())url=anime.screenshots.get(Math.abs((int)Math.floor(episode)-1)%anime.screenshots.size());
            String key=d.request.id+"|"+url;
            poster.setVisibility(url.isEmpty()?View.GONE:View.VISIBLE);placeholder.setVisibility(url.isEmpty()?View.VISIBLE:View.GONE);placeholder.setText("Серия\n"+Ui.number(episode));
            if(!key.equals(imageKey)){imageKey=key;poster.setTag(null);poster.setImageDrawable(null);if(!url.isEmpty())YoruApp.app().images.load(poster,url,key);}
            type.setText("Тип: "+OfflineExporter.format(d));
            androidx.work.WorkInfo export=ExportPresentation.latest(exportStates,d);
            String exportStatus=ExportPresentation.status(export);
            String status=d.state==Download.STATE_COMPLETED&&!exportStatus.isEmpty()?exportStatus:DownloadHub.status(d);
            if(d.state==Download.STATE_QUEUED&&YoruApp.app().store.wifiDownloads()&&YoruApp.app().traffic.metered())status="Ожидает Wi-Fi";
            boolean preparing=hub.preparing(d.request.id);if(preparing)status="Готовим выбранную серию к скачиванию…";
            state.setText(status);state.setTextColor(d.state==Download.STATE_FAILED||(export!=null&&export.getState()==androidx.work.WorkInfo.State.FAILED)?0xffe0a791:Ui.MUTED);
            boolean saving=ExportPresentation.active(exportStates,d);boolean active=preparing||saving||d.state==Download.STATE_DOWNLOADING||d.state==Download.STATE_QUEUED||d.state==Download.STATE_RESTARTING;
            progress.setVisibility(active?View.VISIBLE:View.GONE);float pct=preparing?-1:saving?ExportPresentation.percent(export):d.getPercentDownloaded();progress.setIndeterminate(pct<0);progress.setProgress(pct<0?0:(int)pct);
            String exportSize=ExportPresentation.size(export);
            size.setText(exportSize.isEmpty()?(d.getBytesDownloaded()==0?"0 байт":MediaSize.label(d.getBytesDownloaded()))+(d.contentLength>0?" / "+MediaSize.label(d.contentLength):""):exportSize);
            primary.setText(preparing?"Остановить подготовку":d.state==Download.STATE_COMPLETED?"Смотреть":d.state==Download.STATE_STOPPED?"Продолжить":d.state==Download.STATE_FAILED?"Повторить":"Пауза");
            primary.setEnabled(d.state!=Download.STATE_REMOVING);
            save.setText(saving?"Остановить":export!=null&&export.getState()==androidx.work.WorkInfo.State.FAILED?"Повторить сохранение":"Сохранить");save.setVisibility(d.state==Download.STATE_COMPLETED&&DocumentDownloads.canExport(d)?View.VISIBLE:View.GONE);
        }
    }
}
