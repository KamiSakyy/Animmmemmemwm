package app.yoru.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaSizeTest {
    @Test public void authoritativeRangeTotalIsRead(){assertEquals(123456789L,MediaSize.rangeTotal("bytes 0-0/123456789"));}
    @Test public void unknownAndMalformedRangesAreRejected(){assertEquals(-1,MediaSize.rangeTotal("bytes 0-0/*"));assertEquals(-1,MediaSize.rangeTotal("bytes 0-1/100"));assertEquals(-1,MediaSize.rangeTotal(null));assertEquals(-1,MediaSize.rangeTotal("bytes 0-0/999999999999999999999999"));}
    @Test public void manifestSizeIsNotVideoSize(){assertFalse(MediaSize.singleFile("https://example.com/master.m3u8"));assertFalse(MediaSize.singleFile("https://example.com/clip.mp4?source=master.m3u8"));assertFalse(MediaSize.singleFile("https://example.com/video.mpd"));}
    @Test public void progressiveMediaIsEligible(){assertTrue(MediaSize.singleFile("https://example.com/video.mp4?token=test"));assertTrue(MediaSize.singleFile("https://example.com/video.webm"));}
    @Test public void labelsDoNotEstimateUnknownSize(){assertEquals("Размер неизвестен",MediaSize.label(-1));assertEquals("Размер неизвестен",MediaSize.label(0));assertEquals("123 байт",MediaSize.label(123));}
    @Test public void batchTotalRequiresEveryExactSize(){assertEquals(300L,MediaSize.total(java.util.Arrays.asList(100L,200L)));assertEquals(-1L,MediaSize.total(java.util.Arrays.asList(100L,-1L)));}
    @Test public void batchTotalDoesNotOverflow(){assertEquals(-1L,MediaSize.total(java.util.Arrays.asList(Long.MAX_VALUE,1L)));}
    @Test public void emptyBatchHasNoKnownSize(){assertEquals(-1L,MediaSize.total(java.util.Collections.emptyList()));assertEquals(-1L,MediaSize.total(null));}
}
