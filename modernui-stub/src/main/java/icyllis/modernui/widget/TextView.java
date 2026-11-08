package icyllis.modernui.widget;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;
import icyllis.modernui.view.View;

public class TextView extends View {
    public TextView(Context context) { super(context); }
    public TextView(Context context, AttributeSet attrs) { super(context, attrs); }
    public TextView(Context context, AttributeSet attrs, ResourceId defStyleAttr) { super(context, attrs, defStyleAttr); }
    public TextView(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

    public final void setText(CharSequence text) {}
    public CharSequence getText() { return ""; }
    public void setTextSize(float size) {}
    public void setTextSize(int unit, float size) {}
    public void setTextColor(int color) {}
    public void setHint(CharSequence hint) {}
    public void setHintTextColor(int color) {}
    public void setSingleLine() {}
    public void setSingleLine(boolean singleLine) {}
    public void setGravity(int gravity) {}
    public void setTextStyle(int style) {}
}
