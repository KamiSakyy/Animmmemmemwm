package app.yoru.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.text.*;
import android.widget.*;
import java.util.*;

final class GenrePicker extends LinearLayout {
    private final Activity activity;
    private final TextView button,availability,retry;
    private final ArrayList<String[]> entries=new ArrayList<>();
    private String selected,selectedTitle;
    private Runnable refreshOpen;
    GenrePicker(Activity activity,String[][] initial,String selected,String selectedTitle){super(activity);this.activity=activity;this.selectedTitle=selectedTitle==null?"":selectedTitle;this.selected=selected==null?"":selected;setOrientation(VERTICAL);addView(Ui.text(activity,"Жанр",11,Ui.MUTED,false));button=Ui.button(activity,"Все жанры",false,this::open);addView(button,Ui.lp(activity,-1,48));availability=Ui.text(activity,"",11,Ui.MUTED,false);addView(availability,Ui.lp(activity,-1,-2));retry=Ui.button(activity,"Загрузить все жанры",false,()->{});retry.setVisibility(GONE);addView(retry,Ui.lp(activity,-1,-2));replace(initial);}
    void loading(){availability.setText("Загружаем полный список жанров…");availability.setVisibility(VISIBLE);retry.setVisibility(GONE);}
    void unavailable(Runnable action){availability.setText("Полный список пока недоступен. Выбранный жанр сохранён.");availability.setVisibility(VISIBLE);retry.setOnClickListener(view->action.run());retry.setVisibility(VISIBLE);}
    String selectedId(){return selected;}
    String selectedName(){if(selected.isEmpty())return "";for(String[] row:entries)if(row[0].equals(selected))return row[1];return selectedTitle;}
    void replace(String[][] rows){
        availability.setVisibility(GONE);retry.setVisibility(GONE);entries.clear();entries.add(new String[]{"","Все жанры"});HashSet<String> seen=new HashSet<>();seen.add("");
        if(rows!=null)for(String[] row:rows)if(row!=null&&row.length>=2&&row[0]!=null&&row[1]!=null&&!row[1].trim().isEmpty()&&seen.add(row[0]))entries.add(Arrays.copyOf(row,row.length));
        String name=selectedName();button.setText(name.isEmpty()?(selected.isEmpty()?"Все жанры":"Выбранный жанр"):name);
        if(refreshOpen!=null)refreshOpen.run();
    }
    private void open(){
        LinearLayout body=Ui.column(activity);EditText search=new EditText(activity);search.setSingleLine(true);search.setHint("Найти жанр");search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);search.setTypeface(Ui.typeface(activity,false));body.addView(search,Ui.lp(activity,-1,48));
        ListView list=new ListView(activity);list.setDivider(null);ArrayList<String[]> visible=new ArrayList<>(entries);ArrayList<String> labels=new ArrayList<>();for(String[] row:visible)labels.add(row[1]);
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,labels){@Override public android.view.View getView(int position,android.view.View reused,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,reused,parent);view.setTextColor(Ui.TEXT);view.setTypeface(Ui.typeface(activity,position<visible.size()&&visible.get(position)[0].equals(selected)));return view;}};
        list.setAdapter(adapter);body.addView(list,Ui.lp(activity,-1,300));TextView empty=Ui.text(activity,"Жанр не найден. Попробуйте другое название.",12,Ui.MUTED,false);empty.setVisibility(GONE);body.addView(empty);Dialog dialog=Ui.custom(activity,"Жанры",body,null,null,null,null,"@close");
        Runnable filter=()->{String needle=ApiRepository.plainName(search.getText().toString());visible.clear();labels.clear();for(String[] row:entries)if(needle.isEmpty()||ApiRepository.plainName(row[1]+(row.length>2?" "+row[2]:"")).contains(needle)){visible.add(row);labels.add(row[1]);}adapter.notifyDataSetChanged();empty.setVisibility(visible.isEmpty()?VISIBLE:GONE);};refreshOpen=filter;dialog.setOnDismissListener(d->{if(refreshOpen==filter)refreshOpen=null;});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){filter.run();}public void afterTextChanged(Editable text){}});
        list.setOnItemClickListener((parent,view,index,id)->{if(index<0||index>=visible.size())return;selected=visible.get(index)[0];selectedTitle=visible.get(index)[1];button.setText(selectedTitle);dialog.dismiss();});
    }
}
