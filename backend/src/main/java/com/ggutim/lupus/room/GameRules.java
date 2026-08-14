package com.ggutim.lupus.room;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Global constraints shared across room creation and game-start
 * validation, configurable via {@code lupus.game.*} properties so they
 * can't drift apart.
 */
@ConfigurationProperties(prefix = "lupus.game")
@Validated
public class GameRules {

    @Min(value = 2, message = "minPlayers must be at least 2")
    private int minPlayers = 4;

    @Min(value = 2, message = "maxPlayers must be at least 2")
    private int maxPlayers = 30;

    public int getMinPlayers() {
        return minPlayers;
    }

    public void setMinPlayers(int minPlayers) {
        this.minPlayers = minPlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }
}
