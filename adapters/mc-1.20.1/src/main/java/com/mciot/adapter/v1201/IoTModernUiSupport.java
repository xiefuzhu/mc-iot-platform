package com.mciot.adapter.v1201;

import com.mciot.core.api.BlockPosKey;
import icyllis.modernui.mc.MuiModApi;

/**
 * ModernUI 运行时检测与安全桥接器。
 * 当检测到客户端安装了 ModernUI (icyllis.modernui.mc.MuiModApi) 时，
 * 自动使用 ModernUI 原生 Fragment 矢量界面渲染；若未安装则平滑降级至原版 Screen。
 */
public final class IoTModernUiSupport {
    private static Boolean modernUiLoaded;

    private IoTModernUiSupport() {
    }

    public static boolean isModernUiAvailable() {
        if (modernUiLoaded == null) {
            try {
                Class.forName("icyllis.modernui.mc.MuiModApi", false, IoTModernUiSupport.class.getClassLoader());
                modernUiLoaded = Boolean.TRUE;
            } catch (Throwable ignored) {
                modernUiLoaded = Boolean.FALSE;
            }
        }
        return modernUiLoaded;
    }

    public static boolean tryOpen(BlockPosKey posKey) {
        if (!isModernUiAvailable()) {
            return false;
        }
        try {
            Launcher.open(posKey);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 独立内部类延迟加载，避免未安装 ModernUI 时触发 Fragment 父类校验。
     */
    private static final class Launcher {
        static void open(BlockPosKey posKey) {
            MuiModApi.openScreen(new IoTModernUiFragment(posKey));
        }
    }
}
