package com.github.rinorsi.cadeditor.client;

import com.github.franckyi.guapi.api.Guapi;
import com.github.franckyi.guapi.base.NodeFactoryImpl;
import com.github.franckyi.guapi.base.ScreenHandlerImpl;
import com.github.franckyi.guapi.base.theme.vanilla.VanillaTheme;
import com.github.rinorsi.cadeditor.client.theme.MonochromeTheme;
import com.github.rinorsi.cadeditor.client.debug.DebugLog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ClientInit {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void init() {
        LOGGER.info("初始化 CAD Editor（客户端）");
        Guapi.registerTheme("vanilla", VanillaTheme.INSTANCE);
        Guapi.registerTheme("monochrome", MonochromeTheme.INSTANCE);
        Guapi.setNodeFactory(NodeFactoryImpl.INSTANCE);
        Guapi.setScreenHandler(ScreenHandlerImpl.INSTANCE);
        Guapi.setDefaultLogger(LogManager.getLogger("CAD Editor"));
        Guapi.setExceptionHandler(ModGuapiExceptionHandler.INSTANCE);
    }

    public static void setup() {
        LOGGER.info("设置 CAD Editor（客户端）");
        ClientConfiguration.load();
        syncGuapiConfig();
        UpdateLogRegistry.load();
        Vault.load();
        //TODO attach a built-in help/documentation entry in init, and check missing translations (low priority)
        //TODO reserve hooks for community API integration: skin/recipe libraries and sharing platforms (low priority)
    }

    public static void syncGuapiConfig() {
        Guapi.setDebugMode(ClientConfiguration.INSTANCE.getGuapiDebugMode());
        DebugLog.modeChanged(ClientConfiguration.INSTANCE.getGuapiDebugMode());
        Guapi.setTheme(ClientConfiguration.INSTANCE.getGuapiTheme());
    }
}
