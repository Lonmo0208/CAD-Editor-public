package com.github.rinorsi.cadeditor.common;

import com.github.franckyi.databindings.base.DataBindingsImpl;
import com.github.rinorsi.cadeditor.common.network.NetworkManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class CommonInit {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void init() {
        LOGGER.info("初始化 CAD Editor（通用模块）");
        DataBindingsImpl.init();
    }

    public static void setup() {
        LOGGER.info("设置 CAD Editor（通用模块）");
        NetworkManager.setup();
        CommonConfiguration.load();
        VersionWhitelist.load();
        //TODO later hook CI smoke tests, performance monitoring, and multi-platform consistency initialization here
    }
}

