package dev.novadev.novabank.config.model;

import java.util.Objects;

/**
 * Section {@code integrations} : ce que NovaBank pousse vers d'autres plugins.
 *
 * @param superiorSkyblock2 niveau des iles de SuperiorSkyblock2
 * @param bentoBox          niveau des iles de BentoBox (addon Level ; bonus toujours {@code LEVEL})
 */
public record IntegrationSettings(IslandLevelSettings superiorSkyblock2, IslandLevelSettings bentoBox) {

    /**
     * @throws NullPointerException si un reglage est nul
     */
    public IntegrationSettings {
        Objects.requireNonNull(superiorSkyblock2, "superiorSkyblock2");
        Objects.requireNonNull(bentoBox, "bentoBox");
    }
}
