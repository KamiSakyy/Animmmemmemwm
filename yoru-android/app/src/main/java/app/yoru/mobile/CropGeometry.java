package app.yoru.mobile;

final class CropGeometry {
    private CropGeometry(){}
    static int[] window(int width,int height,float aspect,float zoom,float centerX,float centerY){
        if(width<=0||height<=0)throw new IllegalArgumentException();
        if(!Float.isFinite(aspect)||aspect<=0)aspect=1;
        if(!Float.isFinite(zoom))zoom=1;zoom=Math.max(1,Math.min(8,zoom));
        if(!Float.isFinite(centerX))centerX=.5f;if(!Float.isFinite(centerY))centerY=.5f;
        float w=Math.min(width,height/aspect)/zoom;float h=w*aspect;
        int cropW=Math.max(1,Math.min(width,(int)w)),cropH=Math.max(1,Math.min(height,(int)h));
        int left=Math.max(0,Math.min(width-cropW,Math.round(centerX*width-cropW/2f)));
        int top=Math.max(0,Math.min(height-cropH,Math.round(centerY*height-cropH/2f)));
        return new int[]{left,top,cropW,cropH};
    }
}
