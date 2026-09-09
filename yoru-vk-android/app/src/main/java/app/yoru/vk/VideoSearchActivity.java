package app.yoru.vk;

import android.app.*;
import android.content.*;
import android.os.*;
import android.net.Uri;
import android.net.http.SslError;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;

/** Search only. Playback continues through the unchanged WebActivity. */
public final class VideoSearchActivity extends Activity {
    private WebView web;
    private EditText input;
    private TextView status;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String query,script,openedAddress="";
    private boolean mobile,submitted,destroyed;
    private int generation,attempt;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);query=getIntent().getStringExtra("query");if(query==null)query="";
        mobile=getIntent().getBooleanExtra("mobile",false);
        LinearLayout root=Ui.column(this);Ui.base(this,root);
        LinearLayout top=Ui.row(this);top.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));
        top.addView(Ui.button(this,"‹",false,this::finish));
        TextView title=Ui.text(this,"Поиск видео ВК",18,Ui.TEXT,true);title.setPadding(Ui.dp(this,10),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(Ui.button(this,"ПК / моб.",false,()->{mobile=!mobile;configureMode();search();}));root.addView(top);
        LinearLayout bar=Ui.row(this);bar.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);input=Ui.input(this,"Название, сезон, серия",false);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(250)});input.setText(query);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);bar.addView(input,new LinearLayout.LayoutParams(0,-2,1));bar.addView(Ui.button(this,"Найти",true,this::search));root.addView(bar);
        status=Ui.text(this,"",12,Ui.MUTED,false);status.setPadding(Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12),Ui.dp(this,8));root.addView(status);
        try{web=new WebView(this);try(InputStream stream=getAssets().open("vk-search.js");ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)out.write(buf,0,n);script=out.toString("UTF-8");}}catch(Exception e){status.setText("Не удалось открыть поиск. Обновите Android System WebView. Просмотр по ссылке остаётся доступен на предыдущем экране.");return;}
        WebView.setWebContentsDebuggingEnabled(false);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSafeBrowsingEnabled(true);settings.setMediaPlaybackRequiresUserGesture(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setSupportMultipleWindows(false);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);configureMode();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return false;
                String address=request.getUrl().toString();
                if(openVideo(address))return true;
                if(!WebRoutes.allowedPage(address)){status.setText("Переход на посторонний сайт заблокирован.");return true;}return false;
            }
            @Override public void doUpdateVisitedHistory(WebView view,String address,boolean reload){if(openVideo(address)){view.stopLoading();view.loadUrl(WebRoutes.search(mobile?1:0,query));}}
            @Override public void onPageFinished(WebView view,String address){
                if(destroyed)return;
                if(isSearchPage(address)){if(!submitted)submit(generation);else inspect(generation,0);}else status.setText(""+WebRoutes.origin(address)+" · Если требуется вход, выполните его на странице ВК, затем нажмите «Найти».");
            }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("Сайт ВК не загрузился. Нажмите «Найти» для повтора или переключите ПК / моб.");}
            @Override public void onReceivedSslError(WebView view,SslErrorHandler h,SslError e){h.cancel();status.setText("Ошибка TLS: соединение с сайтом остановлено.");}
            @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){status.setText("WebView остановлен. Вернитесь назад и откройте поиск снова.");((android.view.ViewGroup)view.getParent()).removeView(view);view.destroy();web=null;return true;}
        });
        web.setWebChromeClient(new WebChromeClient(){@Override public void onPermissionRequest(PermissionRequest r){r.deny();}});
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});search();
    }
    private boolean openVideo(String address){try{String video=WebRoutes.videoLink(address);if(!video.equals(openedAddress)){openedAddress=video;Intent play=new Intent(this,WebActivity.class);play.putExtra("url",video);play.putExtra("query",query);startActivity(play);}return true;}catch(IllegalArgumentException e){return false;}}
    private void configureMode(){if(web==null)return;web.getSettings().setUserAgentString(mobile?WebSettings.getDefaultUserAgent(this):"Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36");}
    private boolean isSearchPage(String url){try{Uri u=Uri.parse(url);String h=u.getHost(),p=u.getPath();return ("vkvideo.ru".equals(h)||"m.vkvideo.ru".equals(h)||"www.vkvideo.ru".equals(h))&&(p==null||p.isEmpty()||p.equals("/")||p.equals("/video"));}catch(Exception e){return false;}}
    private void search(){if(web==null)return;String next=input.getText().toString().trim();try{WebRoutes.search(0,next);}catch(Exception e){input.setError(e.getMessage());return;}query=next;generation++;attempt=0;submitted=false;handler.removeCallbacksAndMessages(null);((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);status.setText("Открываем поиск ВК · "+(mobile?"мобильный":"ПК"));web.stopLoading();web.loadUrl(WebRoutes.search(mobile?1:0,query));}
    private void submit(int gen){if(destroyed||web==null||gen!=generation||submitted||!isSearchPage(web.getUrl()))return;
        if(attempt++>=12){status.setText("Поле поиска сайта не появилось. Переключите ПК / моб. или используйте поиск на странице вручную.");return;}
        web.evaluateJavascript(script.replace("__QUERY__",JSONObject.quote(query)),value->{if(destroyed||gen!=generation)return;if("\"submitted\"".equals(value)){submitted=true;status.setText("Запрос отправлен в поле ВК. Ожидаем результаты…");inspect(gen,0);}else handler.postDelayed(()->submit(gen),1000);});
    }
    private void inspect(int gen,int round){if(destroyed||web==null||gen!=generation||!isSearchPage(web.getUrl()))return;
        web.evaluateJavascript("(()=>Array.from(document.querySelectorAll('a[href]')).filter(a=>/video-?\\d+_\\d+/.test(a.href)&&a.getBoundingClientRect().width>0).length)()",count->{if(destroyed||gen!=generation)return;int found=0;try{found=Integer.parseInt(count);}catch(Exception ignored){}if(found>0){status.setText("На странице есть результаты ВК. Выберите ролик; просмотр откроется прежним способом.");}else if(round<10){handler.postDelayed(()->inspect(gen,round+1),1500);}else status.setText("ВК пока не показал ролики. Попробуйте короткое/оригинальное название, Enter в поле сайта или ПК / моб. Вход и CAPTCHA — только на стороне ВК.");});
    }
    @Override protected void onPause(){if(web!=null)web.onPause();super.onPause();}
    @Override protected void onResume(){super.onResume();openedAddress="";if(web!=null)web.onResume();}
    @Override protected void onDestroy(){destroyed=true;generation++;handler.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();web=null;}super.onDestroy();}
}
