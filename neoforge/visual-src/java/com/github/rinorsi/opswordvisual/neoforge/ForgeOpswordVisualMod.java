package com.github.rinorsi.opswordvisual.neoforge;

import com.github.rinorsi.cadeditor.client.RainbowNameHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ForgeOpswordVisualMod.MOD_ID)
public final class ForgeOpswordVisualMod {

    public static final String MOD_ID = "opswordvisual";

    public ForgeOpswordVisualMod(IEventBus modBus, ModContainer container) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(this::onClientTick);
        }
    }

    private void onClientTick(final ClientTickEvent.Post event) {
        RainbowNameHandler.onClientTick();
    }
}
