package app.yoru.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class CropGeometryTest {
    @Test public void portraitCropStaysInsideLandscapeImage(){int[] crop=CropGeometry.window(4000,2000,2,1,.5f,.5f);assertArrayEquals(new int[]{1500,0,1000,2000},crop);}
    @Test public void zoomAndPanClampAtEdges(){int[] crop=CropGeometry.window(1000,2000,2,2,2,-1);assertArrayEquals(new int[]{500,0,500,1000},crop);}
    @Test public void invalidControlsHaveSafeDefaults(){int[] crop=CropGeometry.window(100,100,Float.NaN,Float.NaN,Float.NaN,Float.NaN);assertArrayEquals(new int[]{0,0,100,100},crop);}
    @Test public void tinyImageNeverProducesEmptyCrop(){int[] crop=CropGeometry.window(1,1,2,8,.5f,.5f);assertEquals(1,crop[2]);assertEquals(1,crop[3]);}
    @Test(expected=IllegalArgumentException.class) public void invalidImageRejected(){CropGeometry.window(0,100,1,1,0,0);}
}
