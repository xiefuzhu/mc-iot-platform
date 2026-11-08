package icyllis.modernui.widget;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int SHOW_DIVIDER_NONE = 0;
    public static final int SHOW_DIVIDER_BEGINNING = 1;
    public static final int SHOW_DIVIDER_MIDDLE = 2;
    public static final int SHOW_DIVIDER_END = 4;

    public LinearLayout(Context context) { super(context); }
    public void setOrientation(int orientation) {}
    public void setGravity(int gravity) {}
    public void setHorizontalGravity(int horizontalGravity) {}
    public void setVerticalGravity(int verticalGravity) {}
    public void setShowDividers(int showDividers) {}
    public void setDividerDrawable(Drawable divider) {}
    public void setDividerPadding(int padding) {}

    public static class LayoutParams extends MarginLayoutParams {
        public float weight;
        public int gravity = -1;
        public LayoutParams(int width, int height) { super(width, height); }
        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }
        public LayoutParams(ViewGroup.LayoutParams p) { super(p); }
        public LayoutParams(MarginLayoutParams source) { super(source); }
        public LayoutParams(LayoutParams source) { super(source); }
    }
}
