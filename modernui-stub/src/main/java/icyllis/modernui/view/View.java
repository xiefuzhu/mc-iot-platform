package icyllis.modernui.view;

import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.Drawable;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;

public class View {
    public static final int TEXT_ALIGNMENT_INHERIT = 0;
    public static final int TEXT_ALIGNMENT_GRAVITY = 1;
    public static final int TEXT_ALIGNMENT_TEXT_START = 2;
    public static final int TEXT_ALIGNMENT_TEXT_END = 3;
    public static final int TEXT_ALIGNMENT_CENTER = 4;
    public static final int TEXT_ALIGNMENT_VIEW_START = 5;
    public static final int TEXT_ALIGNMENT_VIEW_END = 6;

    public interface OnClickListener {
        void onClick(View v);
    }

    public View(Context context) {}
    public View(Context context, AttributeSet attrs) {}
    public View(Context context, AttributeSet attrs, ResourceId defStyleAttr) {}
    public View(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) {}

    public final Context getContext() { return null; }
    public int dp(float value) { return 0; }
    public void setPadding(int left, int top, int right, int bottom) {}
    public void setBackground(Drawable background) {}
    public void setOnClickListener(OnClickListener l) {}
    public void setMinimumWidth(int minWidth) {}
    public void setMinimumHeight(int minHeight) {}
    public void setTextAlignment(int textAlignment) {}
    public void setId(int id) {}
    public void invalidate() {}
    public void requestLayout() {}
}
