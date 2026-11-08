package icyllis.modernui.widget;

import icyllis.modernui.view.View;

public interface Checkable {
    void setChecked(boolean checked);
    boolean isChecked();
    void toggle();

    interface OnCheckedChangeListener {
        void onCheckedChanged(View buttonView, boolean isChecked);
    }
}
