package com.doze.timebound;

import java.io.IOException;
import java.net.URISyntaxException;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

/**
 * Registers the plugin's bundled datapack (advancements) with the server.
 *
 * On Paper 1.21+ bundled datapacks are NOT auto-discovered: they must be
 * registered during the DATAPACK_DISCOVERY lifecycle event, which fires before
 * plugins are enabled. That can only be done from a bootstrapper, not from
 * JavaPlugin#onEnable, which is why this class exists.
 *
 * The datapack lives in the jar at /timebound_datapack (pack.mcmeta +
 * data/timebound/advancement/*.json).
 */
public class TimeBoundBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
                try {
                    event.registrar().discoverPack(
                        getClass().getResource("/timebound_datapack").toURI(),
                        "timebound");
                } catch (URISyntaxException | IOException e) {
                    throw new RuntimeException("Failed to load bundled TimeBound datapack", e);
                }
            })
        );
    }
}
