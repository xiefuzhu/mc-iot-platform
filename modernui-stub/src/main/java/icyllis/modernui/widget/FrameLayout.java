package icyllis.modernui.widget;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;
import icyllis.modernui.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public FrameLayout(Context context) { super(context); }
    public FrameLayout(Context context, AttributeSet attrs) { super(context, attrs); }
    public FrameLayout(Context context, AttributeSet attrs, ResourceId defStyleAttr) { super(context, attrs, defStyleAttr); }
    public FrameLayout(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

    public static class LayoutParams extends MarginLayoutParams {
        public static final int UNSPECIFIED_GRAVITY = -1;
        public int gravity = UNSPECIFIED_GRAVITY;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }
        public LayoutParams(ViewGroup.LayoutParams source) { super(source); }
        public LayoutParams(MarginLayoutParams source) { super(source); }
        public LayoutParams(LayoutParams source) { super(source); }
    }
}
