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
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.NestedScrollView;

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
            if(target instanceof NestedScrollView)((NestedScrollView)target).fling(0);
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
            if(dragging&&target!=null&&velocity!=null){velocity.computeCurrentVelocity(1000,maxVelocity);int speed=-(int)velocity.getYVelocity(pointer);if(Math.abs(speed)>=minVelocity){if(target instanceof RecyclerView)((RecyclerView)target).fling(0,speed);else if(target instanceof ScrollView)((ScrollView)target).fling(speed);else if(target instanceof AbsListView)((AbsListView)target).fling(speed);else if(target instanceof NestedScrollView)((NestedScrollView)target).fling(speed);}}
            clearGesture();
        }else if(action==MotionEvent.ACTION_CANCEL)clearGesture();
        return true;
    }

    private static View scrollTarget(View view,float x,float y){
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=group.getChildCount()-1;i>=0;i--){View child=group.getChildAt(i);if(child.getVisibility()!=VISIBLE)continue;float cx=x+group.getScrollX()-child.getX(),cy=y+group.getScrollY()-child.getY();if(cx<0||cy<0||cx>=child.getWidth()||cy>=child.getHeight())continue;View found=scrollTarget(child,cx,cy);if(found!=null)return found;}}
        return view.canScrollVertically(-1)||view.canScrollVertically(1)?view:null;
    }

    private void clearGesture(){if(velocity!=null){velocity.recycle();velocity=null;}target=null;dragging=false;remainder=0;}
    private static void disableActions(View view){AccessibilityDelegateCompat previous=ViewCompat.getAccessibilityDelegate(view);if(!(previous instanceof PreviewAccessibility))ViewCompat.setAccessibilityDelegate(view,new PreviewAccessibility(previous));if(view.isClickable())view.setClickable(false);if(view.isLongClickable())view.setLongClickable(false);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)disableActions(group.getChildAt(i));}}
    private static final class PreviewAccessibility extends AccessibilityDelegateCompat {
        private final AccessibilityDelegateCompat original;
        PreviewAccessibility(AccessibilityDelegateCompat original){this.original=original==null?new AccessibilityDelegateCompat():original;}
        @Override public androidx.core.view.accessibility.AccessibilityNodeProviderCompat getAccessibilityNodeProvider(View host){return original.getAccessibilityNodeProvider(host);}
        @Override public boolean dispatchPopulateAccessibilityEvent(View host,android.view.accessibility.AccessibilityEvent event){return original.dispatchPopulateAccessibilityEvent(host,event);}
        @Override public void onPopulateAccessibilityEvent(View host,android.view.accessibility.AccessibilityEvent event){original.onPopulateAccessibilityEvent(host,event);}
        @Override public void onInitializeAccessibilityEvent(View host,android.view.accessibility.AccessibilityEvent event){original.onInitializeAccessibilityEvent(host,event);}
        @Override public boolean onRequestSendAccessibilityEvent(ViewGroup host,View child,android.view.accessibility.AccessibilityEvent event){return original.onRequestSendAccessibilityEvent(host,child,event);}
        @Override public void sendAccessibilityEvent(View host,int type){original.sendAccessibilityEvent(host,type);}
        @Override public void sendAccessibilityEventUnchecked(View host,android.view.accessibility.AccessibilityEvent event){original.sendAccessibilityEventUnchecked(host,event);}
        private static boolean mutating(View host,int action){return mutating(action)||(host instanceof android.widget.AbsSeekBar&&(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD));}
        private static boolean mutating(int action){return action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_PASTE||action==android.view.accessibility.AccessibilityNodeInfo.ACTION_CUT||action==AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SET_PROGRESS.getId()||action==AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SELECT.getId()||action==AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLEAR_SELECTION.getId();}
        @Override public boolean performAccessibilityAction(View host,int action,android.os.Bundle arguments){return !mutating(host,action)&&original.performAccessibilityAction(host,action,arguments);}
        @Override public void onInitializeAccessibilityNodeInfo(View host,AccessibilityNodeInfoCompat info){
            original.onInitializeAccessibilityNodeInfo(host,info);info.setClickable(false);info.setLongClickable(false);info.setEditable(false);
            for(AccessibilityNodeInfoCompat.AccessibilityActionCompat action:new java.util.ArrayList<>(info.getActionList()))if(mutating(host,action.getId()))info.removeAction(action);
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnGlobalLayoutListener(readOnly);disableActions(this);}
    @Override protected void onDetachedFromWindow(){clearGesture();if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnGlobalLayoutListener(readOnly);super.onDetachedFromWindow();}
}
