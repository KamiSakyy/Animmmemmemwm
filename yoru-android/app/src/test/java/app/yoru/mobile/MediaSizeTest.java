package app.yoru.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaSizeTest {
    @Test public void authoritativeRangeTotalIsRead(){assertEquals(123456789L,MediaSize.rangeTotal("bytes 0-0/123456789"));}
    @Test public void unknownAndMalformedRangesAreRejected(){assertEquals(-1,MediaSize.rangeTotal("bytes 0-0/*"));assertEquals(-1,MediaSize.rangeTotal("bytes 0-1/100"));assertEquals(-1,MediaSize.rangeTotal(null));assertEquals(-1,MediaSize.rangeTotal("bytes 0-0/999999999999999999999999"));}
    @Test public void manifestSizeIsNotVideoSize(){assertFalse(MediaSize.singleFile("https://example.com/master.m3u8"));assertFalse(MediaSize.singleFile("https://example.com/clip.mp4?source=master.m3u8"));assertFalse(MediaSize.singleFile("https://example.com/video.mpd"));}
    @Test public void progressiveMediaIsEligible(){assertTrue(MediaSize.singleFile("https://example.com/video.mp4?token=test"));assertTrue(MediaSize.singleFile("https://example.com/video.webm"));}
    @Test public void labelsDoNotEstimateUnknownSize(){assertEquals("Размер неизвестен",MediaSize.label(-1));assertEquals("Размер неизвестен",MediaSize.label(0));assertEquals("123 байт",MediaSize.label(123));}
}
