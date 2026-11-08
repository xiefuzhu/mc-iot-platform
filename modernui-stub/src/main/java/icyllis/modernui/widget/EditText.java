package icyllis.modernui.widget;

import icyllis.modernui.core.Context;
import icyllis.modernui.resources.ResourceId;
import icyllis.modernui.text.Editable;
import icyllis.modernui.util.AttributeSet;

public class EditText extends TextView {
    public EditText(Context context) { super(context); }
    public EditText(Context context, AttributeSet attrs) { super(context, attrs); }
    public EditText(Context context, AttributeSet attrs, ResourceId defStyleAttr) { super(context, attrs, defStyleAttr); }
    public EditText(Context context, AttributeSet attrs, ResourceId defStyleAttr, ResourceId defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }

    @Override
    public Editable getText() { return null; }
}
