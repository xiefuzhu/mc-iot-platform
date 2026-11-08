package icyllis.modernui.widget;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.util.AttributeSet;

public class Switch extends Button implements Checkable2 {
    public Switch(Context context) { super(context); }
    public Switch(Context context, AttributeSet attrs) { super(context, attrs); }
    public Switch(Context context, AttributeSet attrs, ResourceId defStyleAttr) { super(context, attrs, defStyleAttr); }
    public Switch(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

    @Override
    public int getId() { return 0; }
    @Override
    public void toggle() {}
    @Override
    public final boolean isChecked() { return false; }
    @Override
    public void setChecked(boolean checked) {}
    @Override
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {}
}
