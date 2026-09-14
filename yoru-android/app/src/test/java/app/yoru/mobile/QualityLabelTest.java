package app.yoru.mobile;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class QualityLabelTest {
    @Test public void realLowResolutionIsNotRelabelled(){assertEquals("144p",QualityPlus.name(144));assertEquals("240p",QualityPlus.streamLabel(240));}
    @Test public void unknownResolutionRemainsUnknown(){assertEquals("Оригинал",QualityPlus.streamLabel(0));}
    @Test public void highResolutionLabelsHaveNoQualityBranding(){assertEquals("1440p · 2K",QualityPlus.name(1440));assertEquals("2160p · 4K",QualityPlus.name(2160));}
    @Test public void selectionDoesNotInventStream(){assertEquals(240,QualityPlus.bestAtOrBelow(Arrays.asList(240,1080),720));}
}
