package app.yoru.vk;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
public class LiveShikimoriTest {
    @Before public void enabled(){Assume.assumeTrue("1".equals(System.getenv("RUN_SHIKI_LIVE")));}
    @Test public void firstFeedPage() throws Exception {assertEquals(20,Api.catalog("",1).size());}
    @Test public void secondFeedPage() throws Exception {assertEquals(20,Api.catalog("",2).size());}
    @Test public void russianSearch() throws Exception {assertTrue(Api.catalog("Мастера меча онлайн",1).stream().anyMatch(a->a.id==11757));}
    @Test public void originalSearch() throws Exception {assertTrue(Api.catalog("Sword Art Online",1).stream().anyMatch(a->a.id==11757));}
}
