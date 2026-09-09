package app.yoru.vk;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class CoreTest {
    @Test public void feedMatchesFirstVersion(){assertTrue(Api.catalogQuery("",1).startsWith("{animes(limit:20,page:1,order:ranked,rating:\"!rx\"){"));assertFalse(Api.catalogQuery("",1).contains("search:"));}
    @Test public void secondFeedPage(){assertTrue(Api.catalogQuery("",2).contains("page:2"));}
    @Test public void shikiSearchNotVideoQuery(){String q=Api.catalogQuery("Мастера меча онлайн",1);assertTrue(q.contains("search:\"Мастера меча онлайн\""));assertFalse(q.contains("1 сезон"));}
    @Test public void shikiQueryEscapesInput(){assertTrue(Api.catalogQuery("A \"quoted\" title",1).contains(JSONObject.quote("A \"quoted\" title")));}
    @Test public void swordArtQuery(){assertEquals("Мастера меча онлайн 1 сезон 1 серия",SearchRules.query("Мастера меча онлайн",1,1,false,""));}
    @Test public void alternativeTitle(){assertEquals("Sword Art Online 1 сезон 12 серия AniDUB",SearchRules.query("Sword Art Online",1,12,false,"AniDUB"));}
    @Test public void film(){assertEquals("Твоё имя фильм",SearchRules.query("Твоё имя",0,0,true,""));}
    @Test(expected=IllegalArgumentException.class) public void invalidEpisode(){SearchRules.query("Anime",1,0,false,"");}
    @Test public void seasonHints(){assertEquals(2,SearchRules.season("Sword Art Online II"));assertEquals(3,SearchRules.season("Аниме [ТВ-3]"));assertEquals(1,SearchRules.season("Мастера меча онлайн"));}
    @Test public void episodeBoundary(){assertTrue(SearchRules.episodeMention("Аниме 1 серия",1));assertTrue(SearchRules.episodeMention("Sword Art Online S01E01",1));assertFalse(SearchRules.episodeMention("Аниме 11 серия",1));assertFalse(SearchRules.episodeMention("2021 обзор",1));}
    @Test public void parseGraphqlTitle() throws Exception {JSONObject j=new JSONObject("{\"id\":\"11757\",\"name\":\"Sword Art Online\",\"russian\":\"Мастера меча онлайн\",\"episodes\":25,\"airedOn\":{\"year\":2012},\"poster\":{\"mainUrl\":\"https://shikimori.io/poster.jpg\"}}");Models.Anime a=Models.anime(j,true);assertEquals(11757,a.id);assertEquals(2012,a.year);assertEquals(25,a.episodes);}
    @Test public void parseRestFallback() throws Exception {Models.Anime a=Models.anime(new JSONObject("{\"id\":1,\"name\":\"Test\",\"russian\":\"\",\"aired_on\":\"2020-01-01\",\"image\":{\"original\":\"/a.jpg\"}}"),false);assertEquals("Test",a.title);assertEquals("https://shikimori.io/a.jpg",a.poster);assertEquals(2020,a.year);}
    @Test public void mobileBypassesLegacyRedirect(){String u=WebRoutes.search(1,"Мастера меча онлайн");assertTrue(u.startsWith("https://m.vkvideo.ru/?q=%D0"));assertTrue(u.endsWith("&action=search"));}
    @Test public void desktopModeSwitch(){String q="Мастера меча онлайн 1 серия";assertEquals(WebRoutes.search(0,q),WebRoutes.modeUrl(WebRoutes.search(1,q),true));assertEquals(WebRoutes.search(1,q),WebRoutes.modeUrl(WebRoutes.search(0,q),false));}
    @Test public void modeDoesNotRewriteVideoOrSearchEngine(){String video="https://vkvideo.ru/video-123_456";assertEquals(video,WebRoutes.modeUrl(video,false));String engine=WebRoutes.search(3,"Anime");assertEquals(engine,WebRoutes.modeUrl(engine,true));}
    @Test public void fourRoutes(){for(int i=0;i<4;i++)assertTrue(WebRoutes.allowedPage(WebRoutes.search(i,"Мастера меча онлайн 1 сезон 1 серия")));}
    @Test public void utf8Search() throws Exception {String u=WebRoutes.search(0,"Мастера меча & 1 серия");assertTrue(u.contains("%D0%9C"));assertTrue(u.contains("%26"));assertFalse(u.contains(" "));}
    @Test public void engineScope(){assertTrue(WebRoutes.search(2,"Anime").contains("site%3Avkvideo.ru%2Fvideo"));assertTrue(WebRoutes.search(3,"Anime").contains("site%3Avk.com%2Fvideo"));}
    @Test(expected=IllegalArgumentException.class) public void emptyQuery(){WebRoutes.search(0,"  ");}
    @Test(expected=IllegalArgumentException.class) public void longQuery(){WebRoutes.search(0,new String(new char[251]).replace('\0','a'));}
    @Test(expected=IllegalArgumentException.class) public void badMethod(){WebRoutes.search(9,"Anime");}
    @Test public void normalLoginDomains(){assertTrue(WebRoutes.allowedPage("https://id.vk.com/auth"));assertTrue(WebRoutes.allowedPage("https://login.vk.ru/"));assertTrue(WebRoutes.allowedPage("https://m.vkvideo.ru/"));}
    @Test public void rejectForeignTopFrame(){assertFalse(WebRoutes.allowedPage("https://vk.com.evil.test/video1_2"));assertFalse(WebRoutes.allowedPage("https://evilvk.com/"));assertFalse(WebRoutes.allowedPage("https://vkvideo.ru@evil.test/"));assertFalse(WebRoutes.allowedPage("https://evil.test@vk.com/"));}
    @Test public void rejectNonHttps(){for(String u:new String[]{"http://vk.com/","file:///etc/passwd","javascript:alert(1)","intent://video#Intent;end","data:text/html,Hi","https://vk.com:8080/"})assertFalse(WebRoutes.allowedPage(u));}
    @Test public void directVideo(){assertEquals("https://vkvideo.ru/video-123_456",WebRoutes.videoLink("https://vk.com/video-123_456"));}
    @Test public void positiveOwner(){assertEquals("https://vkvideo.ru/video123_456",WebRoutes.videoLink("vkvideo.ru/video123_456"));}
    @Test public void sharedMessage(){assertEquals("https://vkvideo.ru/video-123_456",WebRoutes.videoLink("Посмотри: https://m.vk.com/video-123_456. "));}
    @Test public void nestedVideo(){assertEquals("https://vkvideo.ru/video-123_456",WebRoutes.videoLink("https://vk.com/videos-123?z=video-123_456%2Fpl_-123_-2"));}
    @Test public void shareList(){assertEquals("https://vkvideo.ru/video-123_456?list=abc123",WebRoutes.videoLink("https://vk.com/video-123_456?list=abc123&utm_source=share"));}
    @Test public void officialEmbed(){assertEquals("https://vk.com/video_ext.php?oid=-123&id=456",WebRoutes.embed("https://vkvideo.ru/video-123_456"));}
    @Test public void preserveProvidedEmbedHash(){assertEquals("https://vk.com/video_ext.php?oid=-123&id=456&hash=abc123",WebRoutes.videoLink("https://vkvideo.ru/video_ext.php?oid=-123&id=456&hash=abc123&autoplay=1"));}
    @Test(expected=IllegalArgumentException.class) public void rejectForeignVideo(){WebRoutes.videoLink("https://evil.test/video-123_456");}
    @Test(expected=IllegalArgumentException.class) public void rejectToken(){WebRoutes.videoLink("https://vk.com/video-123_456#access_token=FAKE_TEST_ONLY");}
    @Test(expected=IllegalArgumentException.class) public void rejectTokenUrl(){WebRoutes.videoLink("https://oauth.vk.com/blank.html#access_token=FAKE_TEST_ONLY");}
    @Test(expected=IllegalArgumentException.class) public void rejectDuplicateQuery(){WebRoutes.videoLink("https://vk.com/video-123_456?list=a&list=b");}
    @Test(expected=IllegalArgumentException.class) public void rejectBrokenEscape(){WebRoutes.videoLink("https://vk.com/video-123_456?list=%ZZ");}
    @Test(expected=IllegalArgumentException.class) public void rejectZeroId(){WebRoutes.videoLink("https://vk.com/video-123_0");}
    @Test(expected=IllegalArgumentException.class) public void rejectPosts(){WebRoutes.videoLink("https://vk.com/wall-123_456");}
    @Test(expected=IllegalArgumentException.class) public void rejectShortLinks(){WebRoutes.videoLink("https://vk.cc/abc123");}
    @Test public void errorDoesNotEchoInput(){try{WebRoutes.videoLink("https://evil.test/?secret=FAKE_TEST_ONLY");fail();}catch(IllegalArgumentException e){assertFalse(e.getMessage().contains("FAKE_TEST_ONLY"));}}
}
