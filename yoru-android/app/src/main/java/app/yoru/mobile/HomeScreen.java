package app.yoru.mobile;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Future;

final class HomeScreen extends SwipeRefreshLayout {
    private final Activity activity;
    private final java.util.function.Consumer<LinearLayout> extras;
    private final ArrayList<Anime> rows=new ArrayList<>();
    private final HashSet<String> identities=new HashSet<>();
    private final FeedAdapter adapter=new FeedAdapter();
    private final RecyclerView list;
    private Future<?> request;
    private int page,generation;
    private boolean loading,more=true,attached,failed;
    private long updated,day=Long.MIN_VALUE;
    private Anime recommendation;
    private final Runnable hourly=this::hourlyRefresh;
    private void hourlyRefresh(){if(attached&&getWindowVisibility()==VISIBLE){if(!loading&&list.getScrollState()==RecyclerView.SCROLL_STATE_IDLE&&(System.currentTimeMillis()-updated>=3600000L||day!=LocalDate.now().toEpochDay()))refreshQuietly();schedule();}}

    HomeScreen(Activity activity,java.util.function.Consumer<LinearLayout> extras){
        super(activity);this.activity=activity;this.extras=extras;
        setColorSchemeColors(Ui.PURPLE);setProgressBackgroundColorSchemeColor(Ui.CARD);
        list=new RecyclerView(activity);
        int columns=Math.max(2,Math.min(4,(int)(activity.getResources().getDisplayMetrics().widthPixels/activity.getResources().getDisplayMetrics().density/170)));
        GridLayoutManager layout=new GridLayoutManager(activity,columns);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){@Override public int getSpanSize(int position){return position==0||position==rows.size()+1?columns:1;}});
        list.setLayoutManager(layout);list.setAdapter(adapter);list.setPadding(Ui.dp(activity,13),Ui.dp(activity,13),Ui.dp(activity,13),Ui.dp(activity,24));list.setClipToPadding(false);
        DefaultItemAnimator animator=new DefaultItemAnimator();animator.setSupportsChangeAnimations(false);list.setItemAnimator(animator);
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView view,int dx,int dy){if(dy>0&&layout.findLastVisibleItemPosition()>=rows.size()-2)load(false);}});
        addView(list,new LayoutParams(-1,-1));setOnRefreshListener(()->load(true));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();attached=true;if(rows.isEmpty())load(true);else if(System.currentTimeMillis()-updated>=3600000L||day!=LocalDate.now().toEpochDay())refreshQuietly();schedule();}
    @Override protected void onDetachedFromWindow(){attached=false;generation++;if(request!=null)request.cancel(true);loading=false;setRefreshing(false);removeCallbacks(hourly);super.onDetachedFromWindow();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(list==null)return;if(visibility!=VISIBLE){generation++;if(request!=null)request.cancel(true);loading=false;setRefreshing(false);removeCallbacks(hourly);}else if(attached){if(rows.isEmpty())load(true);else if(System.currentTimeMillis()-updated>=3600000L||day!=LocalDate.now().toEpochDay())refreshQuietly();schedule();}}
    private void schedule(){removeCallbacks(hourly);if(!attached||getWindowVisibility()!=VISIBLE)return;long now=System.currentTimeMillis(),midnight=LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();long due=Math.min(updated+3600000L,midnight);postDelayed(hourly,due<=now?60000L:Math.max(1000L,due-now));}
    private void refreshQuietly(){
        if(!attached||loading||getWindowVisibility()!=VISIBLE)return;if(rows.isEmpty()){load(true);return;}if(list.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE)return;
        loading=true;int token=++generation;
        request=YoruApp.app().ui.submit(()->{try{Anime.Page result=YoruApp.app().api.homePage(1);TaskQueue.check();post(()->{
            if(!attached||token!=generation)return;loading=false;if(list.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE){schedule();return;}
            GridLayoutManager layout=(GridLayoutManager)list.getLayoutManager();int first=layout==null?0:layout.findFirstVisibleItemPosition();View anchorView=layout==null?null:layout.findViewByPosition(first);int offset=anchorView==null?0:anchorView.getTop()-list.getPaddingTop();String anchor=first>0&&first<=rows.size()?rows.get(first-1).key():"";
            ArrayList<Anime> old=new ArrayList<>(rows);LinkedHashMap<String,Anime> merged=new LinkedHashMap<>();for(Anime anime:YoruBrain.visible(result.items))if(Anime.valid(anime))merged.put(anime.key(),anime);if(result.more)for(Anime anime:old)merged.putIfAbsent(anime.key(),anime);ArrayList<Anime> next=new ArrayList<>(merged.values());
            DiffUtil.DiffResult changes=DiffUtil.calculateDiff(new DiffUtil.Callback(){public int getOldListSize(){return old.size()+2;}public int getNewListSize(){return next.size()+2;}public boolean areItemsTheSame(int a,int b){if(a==0||b==0)return a==b;if(a==old.size()+1||b==next.size()+1)return a==old.size()+1&&b==next.size()+1;return old.get(a-1).key().equals(next.get(b-1).key());}public boolean areContentsTheSame(int a,int b){if(a==0||a==old.size()+1)return false;Anime before=old.get(a-1),after=next.get(b-1);return before==after||before.json().toString().equals(after.json().toString());}});
            failed=false;if(page<=1||!result.more){page=1;more=result.more;}rows.clear();rows.addAll(next);identities.clear();identities.addAll(merged.keySet());updated=System.currentTimeMillis();day=LocalDate.now().toEpochDay();int candidates=Math.min(6,rows.size());recommendation=candidates==0?null:rows.get((int)Math.floorMod(day,(long)candidates));changes.dispatchUpdatesTo(adapter);
            if(layout!=null){if(first==0)layout.scrollToPositionWithOffset(0,offset);else if(!anchor.isEmpty())for(int i=0;i<rows.size();i++)if(anchor.equals(rows.get(i).key())){layout.scrollToPositionWithOffset(i+1,offset);break;}}schedule();
        });}catch(Exception error){post(()->{if(attached&&token==generation){loading=false;schedule();}});}});
        if(request.isCancelled()){loading=false;schedule();}
    }
    private void load(boolean reset){
        if(!attached||getWindowVisibility()!=VISIBLE||(!reset&&(loading||!more)))return;
        if(reset&&request!=null)request.cancel(true);
        loading=true;failed=false;int token=++generation;int next=reset?1:page+1;
        if(reset)setRefreshing(true);else adapter.notifyItemChanged(rows.size()+1);
        request=YoruApp.app().ui.submit(()->{
            try{Anime.Page result=YoruApp.app().api.homePage(next);TaskQueue.check();post(()->{
                if(!attached||token!=generation)return;
                int old=rows.size();if(reset){rows.clear();identities.clear();}
                for(Anime anime:YoruBrain.visible(result.items))if(Anime.valid(anime)&&identities.add(anime.key()))rows.add(anime);
                page=next;more=result.more;loading=false;if(reset)updated=System.currentTimeMillis();setRefreshing(false);
                if(reset){day=LocalDate.now().toEpochDay();recommendation=rows.isEmpty()?null:rows.get((int)Math.floorMod(day,(long)rows.size()));adapter.notifyDataSetChanged();}
                else{int added=rows.size()-old;if(added>0)adapter.notifyItemRangeInserted(old+1,added);adapter.notifyItemChanged(rows.size()+1);}
                schedule();
            });}catch(Exception e){post(()->{if(!attached||token!=generation)return;loading=false;failed=true;setRefreshing(false);adapter.notifyItemChanged(rows.size()+1);});}
        });
        if(request.isCancelled()){loading=false;failed=true;setRefreshing(false);adapter.notifyItemChanged(rows.size()+1);}
    }
    private final class Holder extends RecyclerView.ViewHolder {Holder(View view){super(view);}}
    private final class FeedAdapter extends RecyclerView.Adapter<Holder> {
        @Override public int getItemCount(){return rows.size()+2;}
        @Override public int getItemViewType(int position){return position==0?0:position==rows.size()+1?2:1;}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            View view=type==1?new Ui.Card(activity,220):Ui.column(activity);
            view.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(view);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            if(position>0&&position<=rows.size()){((Ui.Card)holder.itemView).bind(rows.get(position-1));return;}
            LinearLayout box=(LinearLayout)holder.itemView;box.removeAllViews();
            if(position==0){header(box);return;}
            Ui.space(box,14);
            String label=loading?"Загрузка…":failed?"Повторить загрузку":more?"Ещё 6 аниме":"Вы посмотрели весь список";
            TextView button=Ui.button(activity,label,false,()->load(rows.isEmpty()));button.setEnabled(!loading&&(failed||more));box.addView(button,Ui.lp(activity,-1,-2));
        }
    }
    private void header(LinearLayout box){
        box.addView(Ui.label(activity,"РЕКОМЕНДАЦИИ"));Ui.space(box,14);
        Anime anime=recommendation;
        if(anime!=null){
            FrameLayout hero=new FrameLayout(activity);hero.setBackground(Ui.shape(Ui.CARD,20,activity));hero.setClipToOutline(true);
            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);hero.addView(image,new FrameLayout.LayoutParams(-1,-1));YoruApp.app().images.load(image,anime);
            View shade=new View(activity);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22120c1b,0xcc20172d,0xff21162e}));hero.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            LinearLayout text=Ui.column(activity);text.setPadding(Ui.dp(activity,20),Ui.dp(activity,24),Ui.dp(activity,20),Ui.dp(activity,21));text.addView(Ui.label(activity,"Рекомендации"));Ui.space(text,13);
            TextView title=Ui.text(activity,YoruBrain.title(anime),25,Ui.TEXT,true);title.setMaxLines(3);text.addView(title);Ui.space(text,12);text.addView(Ui.text(activity,anime.cardMeta(),11,0xffcdbbdc,false));Ui.space(text,18);
            text.addView(Ui.button(activity,"Подробнее",true,()->Ui.openDetails(activity,anime)),Ui.lp(activity,-1,-2));hero.addView(text,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));box.addView(hero,Ui.lp(activity,-1,340));Ui.space(box,24);
        }
        extras.accept(box);Ui.space(box,24);box.addView(Ui.text(activity,"Топ аниме",23,Ui.TEXT,true));Ui.space(box,12);
    }
}
