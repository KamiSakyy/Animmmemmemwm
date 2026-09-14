package app.yoru.mobile;

import android.app.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import org.json.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

public final class CalendarScreen extends FrameLayout {
    private static final int DAYS=28;
    private static final String[][] FILTERS={{"all","Все"},{"fav","Избранное"},{"new","Новые"},{"soon","Скоро"},{"premiere","Премьеры"},{"ongoing","Выходит"},{"film","Фильмы"}};
    private final androidx.swiperefreshlayout.widget.SwipeRefreshLayout pull;private final Runnable hourly=new Runnable(){public void run(){if(attached){if(!refreshing)load(true);postDelayed(this,3600000L);}}};
    private final Activity activity;private final RecyclerView recycler;private final LinearLayoutManager layout;private final CalendarAdapter adapter;
    private final ArrayList<ApiRepository.AiringItem> items=new ArrayList<>(),allRows=new ArrayList<>(),visible=new ArrayList<>();
    private final HashMap<String,ArrayList<ArrayList<ApiRepository.AiringItem>>> buckets=new HashMap<>();private final HashMap<String,Integer> filterCounts=new HashMap<>();
    private final Locale ru=new Locale("ru");private final long[] starts=new long[DAYS];private final String[] shortDays=new String[DAYS],longDays=new String[DAYS];
    private int selected=-1,generation;private String filter="all",state="Календарь";private boolean generalUnavailable;private boolean attached=true,refreshing,personalRefreshQueued;private Future<?> loadFuture;
    public CalendarScreen(Activity a){super(a);activity=a;setBackgroundColor(Ui.BG);recycler=new RecyclerView(a);layout=new LinearLayoutManager(a);recycler.setLayoutManager(layout);recycler.setHasFixedSize(false);recycler.setItemViewCacheSize(18);recycler.setClipToPadding(false);recycler.setPadding(0,0,0,Ui.dp(a,16));recycler.setVerticalScrollBarEnabled(false);recycler.setHorizontalScrollBarEnabled(false);recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);DefaultItemAnimator animator=new DefaultItemAnimator();animator.setAddDuration(170);animator.setMoveDuration(150);animator.setChangeDuration(120);recycler.setItemAnimator(animator);adapter=new CalendarAdapter();recycler.setAdapter(adapter);pull=new androidx.swiperefreshlayout.widget.SwipeRefreshLayout(a);pull.setColorSchemeColors(Ui.PURPLE);pull.setProgressBackgroundColorSchemeColor(Ui.CARD);pull.addView(recycler,new ViewGroup.LayoutParams(-1,-1));pull.setOnRefreshListener(()->load(true));addView(pull,new FrameLayout.LayoutParams(-1,-1));prepareDays();load(true);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;removeCallbacks(hourly);postDelayed(hourly,3600000L);}
    @Override protected void onDetachedFromWindow(){attached=false;removeCallbacks(hourly);generation++;if(loadFuture!=null)loadFuture.cancel(true);super.onDetachedFromWindow();}
    private void load(){load(true);}
    private void load(boolean force){int gen=++generation;if(loadFuture!=null)loadFuture.cancel(true);prepareDays();refreshing=true;state="Календарь";rebuildVisible(true);adapter.publish();loadFuture=YoruApp.app().discovery.submit(()->{ArrayList<ApiRepository.AiringItem> cached=new ArrayList<>();boolean fresh=false;try{cached=ApiRepository.airingFromJson(YoruApp.app().cache.schedule(6*60*60*1000L));if(cached.isEmpty())cached=ApiRepository.airingFromJson(readLegacyCache());fresh=!cached.isEmpty()&&System.currentTimeMillis()-YoruApp.app().store.calendarCacheAt()<25*60*1000L;}catch(Exception ignored){}final ArrayList<ApiRepository.AiringItem> cacheSnapshot=new ArrayList<>(cached);if(!cacheSnapshot.isEmpty())YoruApp.app().main.post(()->{if(alive()&&gen==generation&&items.isEmpty())setItems(cacheSnapshot,true);});if(fresh&&!force){YoruApp.app().main.post(()->{if(alive()&&gen==generation){refreshing=false;pull.setRefreshing(false);if(!cacheSnapshot.isEmpty())setItems(cacheSnapshot,true);}});return;}try{ArrayList<ApiRepository.AiringItem> loaded=YoruApp.app().api.airingSchedule(DAYS,YoruApp.app().store.favorites());JSONArray json=ApiRepository.airingJson(loaded);YoruApp.app().cache.schedule(json);YoruApp.app().store.calendarCache(json);YoruApp.app().calendarTodayCount=ApiRepository.todayCount(loaded);YoruApp.app().main.post(()->{if(!alive()||gen!=generation)return;refreshing=false;pull.setRefreshing(false);setItems(loaded,true);});}catch(Exception e){YoruApp.app().main.post(()->{if(!alive()||gen!=generation)return;refreshing=false;pull.setRefreshing(false);if(items.isEmpty()&&!cacheSnapshot.isEmpty())setItems(cacheSnapshot,true);else{state="Календарь";rebuildVisible(false);adapter.publish();}});}});if(loadFuture.isCancelled()){refreshing=false;pull.setRefreshing(false);Ui.toast(activity,"Очередь занята. Нажмите обновление позже.");}}
    public void quickShow(){filter="all";selected=-1;prepareDays();if(items.isEmpty()||refreshing){load();return;}buildBuckets();rebuildVisible(false);adapter.publish();}
    private JSONArray readLegacyCache(){try{return new JSONArray(YoruApp.app().store.calendarCache());}catch(Exception e){return new JSONArray();}}
    private boolean alive(){return attached&&!activity.isFinishing();}
    private void setItems(List<ApiRepository.AiringItem> rows,boolean keepPosition){items.clear();if(rows!=null)items.addAll(rows);generalUnavailable=!items.isEmpty();for(ApiRepository.AiringItem item:items)if(!"personal-fallback".equals(item.source)){generalUnavailable=false;break;}buildBuckets();if(selected>=DAYS)selected=-1;rebuildVisible(true);adapter.publish();if(!keepPosition)recycler.scrollToPosition(0);}
    private void buildBuckets(){buckets.clear();filterCounts.clear();for(String[] f:FILTERS){ArrayList<ArrayList<ApiRepository.AiringItem>> days=new ArrayList<>(DAYS);for(int i=0;i<DAYS;i++)days.add(new ArrayList<>());buckets.put(f[0],days);filterCounts.put(f[0],0);}long now=System.currentTimeMillis();SecureStore store=YoruApp.app().store;boolean personal=store!=null&&store.ready();if(!personal&&store!=null&&!personalRefreshQueued){personalRefreshQueued=true;YoruApp.app().io.execute(()->{try{store.preload();}catch(Exception ignored){}YoruApp.app().main.post(()->{personalRefreshQueued=false;if(!alive())return;buildBuckets();rebuildVisible(false);adapter.publish();});});}HashMap<String,Boolean> favMemo=new HashMap<>(),historyMemo=new HashMap<>();HashSet<String> seen=new HashSet<>();for(ApiRepository.AiringItem item:items){if(item==null||item.anime==null)continue;int day=ScheduleClock.dayIndex(starts[0],item.time,zone());if(day<0||day>=DAYS)continue;String eventKey=item.anime.key()+"|"+item.time+"|"+item.episode+"|"+item.kind;if(!seen.add(eventKey))continue;String aKey=item.anime.key();boolean fav=false,history=false;if(personal){Boolean f=favMemo.get(aKey);if(f==null){f=store.favorite(item.anime);favMemo.put(aKey,f);}Boolean h=historyMemo.get(aKey);if(h==null){h=store.progress(item.anime).length()>0;historyMemo.put(aKey,h);}fav=f;history=h;}boolean premiere="Премьера".equals(item.kind);boolean ongoing=item.anime.ongoing()||"Новая серия".equals(item.kind);boolean film=item.anime.type.toLowerCase(Locale.ROOT).contains("фильм");addBucket("all",day,item);if(fav)addBucket("fav",day,item);if(!history&&!fav)addBucket("new",day,item);if(item.time>=now)addBucket("soon",day,item);if(premiere)addBucket("premiere",day,item);if(ongoing)addBucket("ongoing",day,item);if(film)addBucket("film",day,item);}}
    private void addBucket(String key,int day,ApiRepository.AiringItem item){ArrayList<ArrayList<ApiRepository.AiringItem>> days=buckets.get(key);if(days!=null&&day>=0&&day<days.size()){days.get(day).add(item);filterCounts.put(key,filterCounts.getOrDefault(key,0)+1);}}
    private ArrayList<ApiRepository.AiringItem> dayItems(int day){ArrayList<ArrayList<ApiRepository.AiringItem>> days=buckets.get(filter);ArrayList<ApiRepository.AiringItem> out=new ArrayList<>();if(days==null)return out;if(day<0){for(ArrayList<ApiRepository.AiringItem> d:days)out.addAll(d);}else if(day<days.size())out.addAll(days.get(day));out.sort((a,b)->Long.compare(a.time,b.time));return out;}
    private void rebuildVisible(boolean reset){allRows.clear();allRows.addAll(dayItems(selected));visible.clear();int total=CalendarFeed.displayCount(allRows.size());if(total>0)visible.addAll(allRows.subList(0,total));state=selected<0?"Все события · "+total:longDays[Math.max(0,Math.min(DAYS-1,selected))]+" · "+total;if(generalUnavailable)state+=" · общее расписание недоступно, показана доступная часть";}
    private int filteredCount(){return countForFilter(filter);}
    private int countForFilter(String key){Integer n=filterCounts.get(key);return n==null?0:n;}
    private int dayCount(String key,int day){ArrayList<ArrayList<ApiRepository.AiringItem>> days=buckets.get(key);return days==null||day<0||day>=days.size()?0:days.get(day).size();}
    private int exactCount(){int n=0;ArrayList<ArrayList<ApiRepository.AiringItem>> days=buckets.get(filter);if(days!=null)for(ArrayList<ApiRepository.AiringItem> d:days)for(ApiRepository.AiringItem item:d)if("Точная дата".equals(item.precision)||"Премьера".equals(item.precision))n++;return n;}
    private int premiereCount(){return countForFilter("premiere");}
    private String summaryLine(){String base=filteredCount()+" событий · "+exactCount()+" с точным временем";if(premiereCount()>0)base+=" · премьер: "+premiereCount();return base;}
    private String nearestTitle(){long now=System.currentTimeMillis();ApiRepository.AiringItem best=null,past=null;List<ApiRepository.AiringItem> src=allRows.isEmpty()?visible:allRows;for(ApiRepository.AiringItem item:src){if(item.time>=now&&(best==null||item.time<best.time))best=item;if(item.time<now&&(past==null||item.time>past.time))past=item;}if(best==null)best=past;return best==null?"Календарь YORU":(best.time<System.currentTimeMillis()?"Уже вышло: ":"Ближайшая серия: ")+(best.anime==null?"аниме":YoruBrain.title(best.anime));}
    private int nearestIndex(){long now=System.currentTimeMillis();int best=-1,past=-1;for(int i=0;i<allRows.size();i++){ApiRepository.AiringItem item=allRows.get(i);if(item.time>=now&&(best<0||item.time<allRows.get(best).time))best=i;if(item.time<now&&(past<0||item.time>allRows.get(past).time))past=i;}return best>=0?best:past;}
    private void jumpNearest(){int index=nearestIndex();if(index>=0)recycler.smoothScrollToPosition(index+1);}
    private int kindColor(ApiRepository.AiringItem item){if(item==null)return Ui.PURPLE;if("Премьера".equals(item.kind))return 0xffffcf70;if(item.time<System.currentTimeMillis())return 0xff88e0a0;if(item.anime!=null&&item.anime.ongoing())return Ui.PURPLE;return 0xff9ec7ff;}
    private String statusSuffix(ApiRepository.AiringItem item){try{if(item!=null&&item.anime!=null){if(YoruApp.app().store.ready()&&YoruApp.app().store.favorite(item.anime))return " · в коллекции";if(item.anime.year>0)return " · "+item.anime.year;}}catch(Exception ignored){}return "";}
    private java.time.ZoneId zone(){return ScheduleClock.zone(YoruApp.app().store.localScheduleTime());}
    private void prepareDays(){long now=System.currentTimeMillis();java.time.ZoneId zone=zone();for(int i=0;i<DAYS;i++){starts[i]=ScheduleClock.dayStart(now,i,zone);shortDays[i]=i==0?"Сегодня":i==1?"Завтра":ScheduleClock.format(starts[i],"EEE dd.MM",zone);longDays[i]=i==0?"Сегодня":i==1?"Завтра":ScheduleClock.format(starts[i],"EEEE, dd MMMM",zone);}}
    private String eventTime(ApiRepository.AiringItem item){return "Точная дата".equals(item.precision)?time(item.time):"время уточняется";}
    private String time(long t){return ScheduleClock.time(t,zone());}
    private String countdown(long t){long diff=t-System.currentTimeMillis();if(diff<=-90*60*1000)return "уже вышло";if(diff<=0)return "выходит сейчас";long min=diff/60000,h=min/60,d=h/24;if(d>0)return "через "+d+" д "+(h%24)+" ч";if(h>0)return "через "+h+" ч "+(min%60)+" мин";return "через "+Math.max(1,min)+" мин";}
    private final class CalendarEntry {
        final int type;
        final ApiRepository.AiringItem item;
        final String key,signature;
        CalendarEntry(int type,ApiRepository.AiringItem item) {
            this.type=type;this.item=item;
            key=item==null?"section:"+type:item.anime.key()+"|"+item.episode+"|"+item.kind;
            signature=item==null?state+"|"+filter+"|"+selected+"|"+filterCounts.toString()+"|"+Arrays.toString(shortDays)+"|"+summaryLine()+"|"+nearestTitle()+"|"+allRows.size()+"|"+visible.size():YoruBrain.title(item.anime)+"|"+item.anime.poster+"|"+item.time+"|"+item.precision+"|"+statusSuffix(item)+"|"+countdown(item.time);
        }
    }

    private final class CalendarAdapter extends RecyclerView.Adapter<CalendarHolder> {
        private ArrayList<CalendarEntry> rows=new ArrayList<>();
        void publish() {
            ArrayList<CalendarEntry> next=new ArrayList<>();
            next.add(new CalendarEntry(0,null));
            if(visible.isEmpty())next.add(new CalendarEntry(2,null));
            else for(ApiRepository.AiringItem item:visible)next.add(new CalendarEntry(1,item));
            ArrayList<CalendarEntry> old=rows;
            DiffUtil.DiffResult result=DiffUtil.calculateDiff(new DiffUtil.Callback() {
                public int getOldListSize(){return old.size();}
                public int getNewListSize(){return next.size();}
                public boolean areItemsTheSame(int a,int b){return old.get(a).key.equals(next.get(b).key);}
                public boolean areContentsTheSame(int a,int b){return old.get(a).signature.equals(next.get(b).signature);}
                public Object getChangePayload(int a,int b){return Boolean.TRUE;}
            });
            rows=next;result.dispatchUpdatesTo(this);
        }
        public int getItemCount(){return rows.size();}
        public int getItemViewType(int position){return rows.get(position).type;}
        public CalendarHolder onCreateViewHolder(ViewGroup parent,int type){return new CalendarHolder(type);}
        public void onBindViewHolder(CalendarHolder holder,int position){holder.bind(rows.get(position));}
        public void onBindViewHolder(CalendarHolder holder,int position,List<Object> payloads){holder.bind(rows.get(position));}
        public void onViewRecycled(CalendarHolder holder){holder.item=null;if(holder.poster!=null){holder.poster.setTag(null);holder.poster.setImageDrawable(null);holder.imageKey="";}}
    }

    private final class CalendarHolder extends RecyclerView.ViewHolder {
        final FrameLayout box;
        HeaderView header;
        ImageView poster;
        TextView title,line,meta,action;
        ApiRepository.AiringItem item;
        String imageKey="";
        CalendarHolder(int type) {
            super(new FrameLayout(activity));box=(FrameLayout)itemView;
            box.setPadding(Ui.dp(activity,16),0,Ui.dp(activity,16),type==0?0:Ui.dp(activity,9));
            box.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            if(type==0){header=new HeaderView();box.addView(header);return;}
            LinearLayout card=type==1?Ui.row(activity):Ui.column(activity);
            card.setPadding(Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12),Ui.dp(activity,12));
            card.setBackground(Ui.stroke(Ui.CARD,16,activity));box.addView(card,new FrameLayout.LayoutParams(-1,-2));
            if(type==1) {
                card.setGravity(Gravity.CENTER_VERTICAL);
                poster=new ImageView(activity);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
                poster.setBackground(Ui.shape(Ui.SURFACE,12,activity));poster.setClipToOutline(true);card.addView(poster,Ui.lp(activity,54,76));
                LinearLayout text=Ui.column(activity);text.setPadding(Ui.dp(activity,12),0,0,0);
                title=Ui.text(activity,"",14,Ui.TEXT,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);text.addView(title);
                Ui.space(text,6);line=Ui.text(activity,"",11,Ui.PURPLE,true);text.addView(line);
                Ui.space(text,5);meta=Ui.text(activity,"",10,Ui.MUTED,false);meta.setSingleLine();meta.setEllipsize(TextUtils.TruncateAt.END);text.addView(meta);
                card.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                View download=Ui.iconButton(activity,"download","Скачать после выхода",()->{
                    if(item==null)return;
                    ScheduledDownloads.choose(activity,item.anime,item.episode,"Точная дата".equals(item.precision)?item.time:0,ScheduleClock.format(item.time,"dd.MM.yyyy",zone())+" "+eventTime(item)+" · "+item.precision);
                });card.addView(download,Ui.lp(activity,44,44));
                Ui.press(card);card.setOnClickListener(v->{if(item!=null)Ui.openDetails(activity,item.anime);});
                card.setOnLongClickListener(v->{if(item==null)return false;Ui.bucketDialog(activity,item.anime,()->{buildBuckets();rebuildVisible(true);adapter.publish();});return true;});
            } else {
                title=Ui.text(activity,"",15,Ui.TEXT,true);card.addView(title);
                Ui.space(card,8);line=Ui.text(activity,"",12,Ui.MUTED,false);card.addView(line);
                Ui.space(card,10);action=Ui.button(activity,"",false,()->{
                    if(items.isEmpty())load();
                    else {filter="all";selected=-1;rebuildVisible(true);adapter.publish();recycler.scrollToPosition(0);}
                });card.addView(action);
            }
        }
        void bind(CalendarEntry entry) {
            item=entry.item;
            if(entry.type==0){header.bind();return;}
            if(entry.type==1) {
                title.setText(YoruBrain.title(item.anime));line.setText(item.kind+" · серия "+Ui.number(item.episode)+" · "+eventTime(item));line.setTextColor(kindColor(item));
                meta.setText(countdown(item.time)+" · "+item.precision+statusSuffix(item));
                String key=item.anime.key()+"|"+item.anime.poster;
                if(!key.equals(imageKey)){imageKey=key;poster.setTag(null);poster.setImageDrawable(null);YoruApp.app().images.load(poster,item.anime);}
                box.setContentDescription(YoruBrain.title(item.anime)+". "+line.getText()+". "+meta.getText());
            } else if(entry.type==2) {
                title.setText(items.isEmpty()?"Пока нет событий":"В этом фильтре пока пусто");
                line.setText(items.isEmpty()?"События появятся автоматически.":"Выберите другой фильтр, другой день или вернитесь к режиму «Все».");
                action.setText(items.isEmpty()?"Обновить":"Показать все");action.setVisibility(View.VISIBLE);
            }
        }
    }

    private final class HeaderView extends LinearLayout {
        final TextView title,info,status,all;
        final TextView[] days=new TextView[DAYS],filters=new TextView[FILTERS.length];
        HeaderView() {
            super(activity);setOrientation(VERTICAL);setPadding(0,Ui.dp(activity,12),0,Ui.dp(activity,10));
            addView(Ui.label(activity,"КАЛЕНДАРЬ YORU"));Ui.space(this,9);addView(Ui.text(activity,"Выход серий",28,Ui.TEXT,true));
            Ui.space(this,7);addView(Ui.text(activity,"Все ближайшие аниме одним списком.",12,Ui.MUTED,false));Ui.space(this,12);
            LinearLayout hero=Ui.column(activity);hero.setPadding(Ui.dp(activity,15),Ui.dp(activity,14),Ui.dp(activity,15),Ui.dp(activity,14));hero.setBackground(Ui.gradient(0xff2e2140,0xff181220,18,activity));
            title=Ui.text(activity,"",19,Ui.TEXT,true);title.setMaxLines(3);title.setEllipsize(TextUtils.TruncateAt.END);hero.addView(title);
            Ui.space(hero,7);info=Ui.text(activity,"",11,0xffd8c7ea,false);hero.addView(info);
            LinearLayout actions=Ui.row(activity);actions.setGravity(Gravity.RIGHT);actions.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));actions.addView(Ui.iconButton(activity,"refresh","Обновить",CalendarScreen.this::load),Ui.lp(activity,42,42));hero.addView(actions);addView(hero);
            Ui.space(this,13);LinearLayout dayRow=horizontal();
            all=chip(dayRow,()->{if(selected<0)return;selected=-1;select();});
            for(int i=0;i<DAYS;i++){final int index=i;days[i]=chip(dayRow,()->{if(selected==index)return;selected=index;select();});}
            Ui.space(this,9);LinearLayout filterRow=horizontal();
            for(int i=0;i<FILTERS.length;i++){final String value=FILTERS[i][0];filters[i]=chip(filterRow,()->{if(value.equals(filter))return;filter=value;selected=-1;select();});}
            Ui.space(this,10);status=Ui.text(activity,"",12,Ui.MUTED,false);addView(status);
        }
        LinearLayout horizontal(){HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(activity);scroll.addView(row);addView(scroll,Ui.lp(activity,-1,-2));return row;}
        TextView chip(LinearLayout row,Runnable click){TextView view=Ui.chip(activity,"",false,click);LinearLayout.LayoutParams p=Ui.lp(activity,-2,-2);p.rightMargin=Ui.dp(activity,7);row.addView(view,p);return view;}
        void select(){rebuildVisible(true);adapter.publish();recycler.scrollToPosition(0);}
        void style(TextView view,String value,boolean active){view.setText(value);view.setTextColor(active?0xff21152f:Ui.MUTED);view.setBackground(active?Ui.gradient(0xffe2ccff,Ui.PURPLE,13,activity):Ui.stroke(Ui.SURFACE,13,activity));view.setSelected(active);}
        void bind(){
            title.setText(visible.isEmpty()?"Календарь YORU":nearestTitle());info.setText(items.isEmpty()?"События появятся автоматически.":summaryLine());status.setText(state);
            style(all,"Все дни · "+filteredCount(),selected<0);
            for(int i=0;i<DAYS;i++)style(days[i],shortDays[i]+" · "+dayCount(filter,i),selected==i);
            for(int i=0;i<FILTERS.length;i++)style(filters[i],FILTERS[i][1]+" · "+countForFilter(FILTERS[i][0]),FILTERS[i][0].equals(filter));
        }
    }
}
