package app.yoru.vk;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class CoreTest {
    private static final String TOKEN="vk1.a.FAKE_TEST_ONLY_NOT_A_REAL_TOKEN_123456";
    @Test public void rawToken(){assertEquals(TOKEN,TokenParser.parse("  "+TOKEN+"  "));}
    @Test public void fragment(){assertEquals(TOKEN,TokenParser.parse("https://oauth.vk.com/blank.html#access_token="+TOKEN+"&expires_in=0&user_id=42"));}
    @Test public void query(){assertEquals(TOKEN,TokenParser.parse("https://api.vk.com/blank.html?access_token="+TOKEN+"&user_id=42"));}
    @Test public void tokenParameter(){assertEquals(TOKEN,TokenParser.parse("access_token="+TOKEN+"&expires_in=0"));}
    @Test public void encodedValue(){assertEquals(TOKEN,TokenParser.parse("https://oauth.vk.com/blank.html#access_token="+TOKEN.replace(".","%2E")));}
    @Test public void encodedLink(){assertEquals(TOKEN,TokenParser.parse("https%3A%2F%2Foauth.vk.com%2Fblank.html%23access_token%3D"+TOKEN));}
    @Test public void plusPreserved(){assertEquals(TOKEN+"+ab",TokenParser.parse("https://oauth.vk.com/blank.html#access_token="+TOKEN+"%2Bab"));}
    @Test(expected=IllegalArgumentException.class) public void rejectForeignLink(){TokenParser.parse("https://vk.com.evil.test/#access_token="+TOKEN);}
    @Test(expected=IllegalArgumentException.class) public void rejectDuplicate(){TokenParser.parse("https://vk.com/?access_token="+TOKEN+"#access_token="+TOKEN);}
    @Test(expected=IllegalArgumentException.class) public void rejectUserInfo(){TokenParser.parse("https://user@vk.com/#access_token="+TOKEN);}
    @Test(expected=IllegalArgumentException.class) public void rejectBrokenEscape(){TokenParser.parse("access_token=%ZZ");}
    @Test(expected=IllegalArgumentException.class) public void rejectEmpty(){TokenParser.parse("https://vk.com/#access_token=");}
    @Test public void errorDoesNotEchoToken(){try{TokenParser.parse("https://evil.invalid/#access_token="+TOKEN);fail();}catch(IllegalArgumentException e){assertFalse(e.getMessage().contains(TOKEN));}}
    @Test public void swordArtQuery(){assertEquals("Мастера меча онлайн 1 сезон 1 серия",SearchRules.query("Мастера меча онлайн",1,1,false,""));}
    @Test public void alternativeTitle(){assertEquals("Sword Art Online 1 сезон 12 серия AniDUB",SearchRules.query("Sword Art Online",1,12,false,"AniDUB"));}
    @Test public void film(){assertEquals("Твоё имя фильм",SearchRules.query("Твоё имя",0,0,true,""));}
    @Test(expected=IllegalArgumentException.class) public void invalidEpisode(){SearchRules.query("Anime",1,0,false,"");}
    @Test public void seasonHints(){assertEquals(2,SearchRules.season("Sword Art Online II"));assertEquals(3,SearchRules.season("Аниме [ТВ-3]"));assertEquals(1,SearchRules.season("Мастера меча онлайн"));}
    @Test public void episodeBoundary(){assertTrue(SearchRules.episodeMention("Аниме 1 серия",1));assertTrue(SearchRules.episodeMention("Sword Art Online S01E01",1));assertFalse(SearchRules.episodeMention("Аниме 11 серия",1));assertFalse(SearchRules.episodeMention("2021 обзор",1));}
    @Test public void preferSupportedDirectFiles() throws Exception {JSONObject f=new JSONObject().put("mp4_720","https://sun1.vkuser.net/video.mp4").put("mp4_1080","https://vkvideo.ru/video.mp4").put("external","https://example.com/embed");assertEquals("1080p",Models.streams(f).keySet().iterator().next());assertEquals(2,Models.streams(f).size());}
    @Test public void rejectUnsafeMedia(){assertFalse(Models.safeMedia("http://vkuser.net/video"));assertFalse(Models.safeMedia("https://vkuser.net.evil.test/video"));assertFalse(Models.safeMedia("https://127.0.0.1/video"));assertFalse(Models.safeMedia("https://user@vk.com/video"));assertTrue(Models.safeMedia("https://vkvd123.mycdn.me/manifest.m3u8"));}
    @Test public void playerFieldIsNotDirectVideo() throws Exception {assertTrue(Models.streams(new JSONObject().put("player","https://vk.com/video_ext.php")).isEmpty());}
    @Test public void deDuplicateVideos() throws Exception {JSONObject v=new JSONObject().put("owner_id",-123).put("id",456).put("title","Серия 1");JSONObject r=new JSONObject().put("items",new JSONArray().put(v).put(v));assertEquals(1,Models.videos(r).size());assertEquals("-123_456",Models.videos(r).get(0).key());}
    @Test public void parseGraphqlTitle() throws Exception {JSONObject j=new JSONObject("{\"id\":\"11757\",\"name\":\"Sword Art Online\",\"russian\":\"Мастера меча онлайн\",\"episodes\":25,\"airedOn\":{\"year\":2012},\"poster\":{\"mainUrl\":\"https://shikimori.io/poster.jpg\"}}");Models.Anime a=Models.anime(j,true);assertEquals(11757,a.id);assertEquals(2012,a.year);assertEquals(25,a.episodes);}
    @Test public void parseRestFallback() throws Exception {Models.Anime a=Models.anime(new JSONObject("{\"id\":1,\"name\":\"Test\",\"russian\":\"\",\"aired_on\":\"2020-01-01\",\"image\":{\"original\":\"/a.jpg\"}}"),false);assertEquals("Test",a.title);assertEquals("https://shikimori.io/a.jpg",a.poster);assertEquals(2020,a.year);}
    @Test public void permissionErrorsAreExplicit(){assertTrue(Api.vkError(7).contains("video"));assertTrue(Api.vkError(5).contains("Токен"));assertTrue(Api.vkError(14).contains("CAPTCHA"));}
}
