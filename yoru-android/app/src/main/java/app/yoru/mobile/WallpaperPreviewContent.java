package app.yoru.mobile;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.AbsListView;
import androidx.recyclerview.widget.RecyclerView;

final class WallpaperPreviewContent extends FrameLayout {
    private final int slop,minVelocity,maxVelocity;
    private View target;
    private VelocityTracker velocity;
    private float startY,lastY,remainder;
    private int pointer;
    private boolean dragging;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener readOnly=()->disableActions(this);

    WallpaperPreviewContent(Context context){super(context);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);ViewConfiguration config=ViewConfiguration.get(context);slop=config.getScaledTouchSlop();minVelocity=config.getScaledMinimumFlingVelocity();maxVelocity=config.getScaledMaximumFlingVelocity();}

    @Override public boolean onInterceptTouchEvent(MotionEvent event){return true;}

    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            clearGesture();pointer=event.getPointerId(0);startY=lastY=event.getY();target=scrollTarget(this,event.getX(),event.getY());
            if(target instanceof RecyclerView)((RecyclerView)target).stopScroll();
            if(target instanceof ScrollView)((ScrollView)target).fling(0);
            if(target instanceof AbsListView)((AbsListView)target).fling(0);
            velocity=VelocityTracker.obtain();velocity.addMovement(event);return true;
        }
        if(velocity!=null)velocity.addMovement(event);
        if(action==MotionEvent.ACTION_MOVE){
            int index=event.findPointerIndex(pointer);if(index<0)return true;float y=event.getY(index);
            if(!dragging&&Math.abs(y-startY)>slop){dragging=true;lastY=startY+(y>startY?slop:-slop);}
            if(dragging&&target!=null){float distance=lastY-y+remainder;int pixels=(int)distance;remainder=distance-pixels;if(target instanceof AbsListView)((AbsListView)target).scrollListBy(pixels);else target.scrollBy(0,pixels);}lastY=y;
        }else if(action==MotionEvent.ACTION_POINTER_UP){
            int index=event.getActionIndex();if(event.getPointerId(index)==pointer){int next=index==0?1:0;pointer=event.getPointerId(next);lastY=startY=event.getY(next);remainder=0;if(velocity!=null)velocity.clear();}
        }else if(action==MotionEvent.ACTION_UP){
            if(dragging&&target!=null&&velocity!=null){velocity.computeCurrentVelocity(1000,maxVelocity);int speed=-(int)velocity.getYVelocity(pointer);if(Math.abs(speed)>=minVelocity){if(target instanceof RecyclerView)((RecyclerView)target).fling(0,speed);else if(target instanceof ScrollView)((ScrollView)target).fling(speed);else if(target instanceof AbsListView)((AbsListView)target).fling(speed);}}
            clearGesture();
        }else if(action==MotionEvent.ACTION_CANCEL)clearGesture();
        return true;
    }

    private static View scrollTarget(View view,float x,float y){
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--){View child=group.getChildAt(i);if(child.getVisibility()!=VISIBLE)continue;float cx=x+group.getScrollX()-child.getX(),cy=y+group.getScrollY()-child.getY();if(cx<0||cy<0||cx>=child.getWidth()||cy>=child.getHeight())continue;View found=scrollTarget(child,cx,cy);if(found!=null)return found;}}
        return view.canScrollVertically(-1)||view.canScrollVertically(1)?view:null;
    }

    private void clearGesture(){if(velocity!=null){velocity.recycle();velocity=null;}target=null;dragging=false;remainder=0;}
    private static void disableActions(View view){if(view.isClickable())view.setClickable(false);if(view.isLongClickable())view.setLongClickable(false);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)disableActions(group.getChildAt(i));}}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnGlobalLayoutListener(readOnly);disableActions(this);}
    @Override protected void onDetachedFromWindow(){clearGesture();if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnGlobalLayoutListener(readOnly);super.onDetachedFromWindow();}
}
