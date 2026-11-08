package icyllis.modernui.view;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;

public abstract class ViewGroup extends View {
    public ViewGroup(Context context) { super(context); }
    public ViewGroup(Context context, AttributeSet attrs) { super(context, attrs); }
    public ViewGroup(Context context, AttributeSet attrs, ResourceId defStyleAttr) { super(context, attrs, defStyleAttr); }
    public ViewGroup(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

    public void addView(View child) {}
    public void addView(View child, int index) {}
    public void addView(View child, int width, int height) {}
    public void addView(View child, LayoutParams params) {}
    public void addView(View child, int index, LayoutParams params) {}
    public void setClipToPadding(boolean clipToPadding) {}

    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;
        public LayoutParams(int width, int height) {
            this.width = width;
            this.height = height;
        }
        public LayoutParams(LayoutParams source) {}
        LayoutParams() {}
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;
        public MarginLayoutParams(int width, int height) { super(width, height); }
        public MarginLayoutParams(MarginLayoutParams source) { super(source); }
        public MarginLayoutParams(LayoutParams source) { super(source); }
        public void setMargins(int left, int top, int right, int bottom) {}
        public void setMarginsRelative(int start, int top, int end, int bottom) {}
    }
}
