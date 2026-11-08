package icyllis.modernui.widget;

import icyllis.modernui.core.Context;

public final class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    public static Toast makeText(CharSequence text, int duration) { return new Toast(); }
    public static Toast makeText(Context context, CharSequence text, int duration) { return new Toast(); }
    public void show() {}
    public void cancel() {}
}
