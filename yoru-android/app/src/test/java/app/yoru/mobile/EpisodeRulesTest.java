package app.yoru.mobile;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class EpisodeRulesTest {
    private Anime.Episode episode(double number,boolean future){Anime.Episode value=new Anime.Episode();value.number=number;value.future=future;return value;}
    @Test public void placeholdersAndDuplicatesDoNotInflateAired(){assertEquals(2,EpisodeRules.available(Arrays.asList(episode(1,false),episode(1,false),episode(2,false),episode(3,true),episode(2.5,false))));}
    @Test public void confirmedSeasonTotalExcludesForeignEpisodes(){Anime catalog=new Anime();catalog.source="yoru";catalog.shikimoriEpisodes=24;List<Anime.Episode> rows=EpisodeRules.visible(catalog,Arrays.asList(episode(24,false),episode(25,false),episode(36,false)));assertEquals(1,rows.size());assertEquals(24,rows.get(0).number,0);}
    @Test public void unverifiedTotalDoesNotHideRealSourceEpisodes(){Anime catalog=new Anime();catalog.source="yummy";catalog.episodes=12;assertEquals(1,EpisodeRules.visible(catalog,Arrays.asList(episode(13,false))).size());}
    @Test public void availableEpisodeWinsOverFuturePlaceholder(){Anime.Episode real=episode(3,false);List<Anime.Episode> rows=EpisodeRules.visible(null,Arrays.asList(episode(3,true),real));assertSame(real,rows.get(0));assertFalse(real.future);}
    @Test public void availableCountNeverDeclaresUnknownStatusFinished(){Anime anime=new Anime();anime.episodes=1;anime.episodeList.add(episode(1,false));assertEquals("Статус уточняется",anime.statusLabel());}
    @Test public void foreignProviderScoreIsNotLabelledShikimori(){Anime anime=new Anime();anime.source="yummy";anime.score=9.9;assertEquals(0,anime.ratingScore(),0);anime.shikimoriScore=8.4;assertEquals(8.4,anime.ratingScore(),0);}
    @Test public void differentSeasonIdsAreNotAbsorbed(){Anime target=new Anime(),source=new Anime();target.malId=400;target.episodes=12;source.malId=100;source.shikimoriEpisodes=36;SourceEngine.absorb(target,source);assertEquals(12,target.episodes);assertEquals(0,target.shikimoriEpisodes);}
}
