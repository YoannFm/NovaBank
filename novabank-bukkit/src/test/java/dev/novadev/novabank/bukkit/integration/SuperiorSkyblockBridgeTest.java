package dev.novadev.novabank.bukkit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.novadev.novabank.api.entity.EntityRef;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuperiorSkyblockBridgeTest {

    @Test
    void readsIslandUuid() {
        UUID id = UUID.randomUUID();
        assertThat(SuperiorSkyblockBridge.islandId(new EntityRef("island", id.toString()))).contains(id);
    }

    @Test
    void unreadableIslandIdDecidesNothing() {
        assertThat(SuperiorSkyblockBridge.islandId(new EntityRef("island", "pas-un-uuid"))).isEmpty();
    }
}
