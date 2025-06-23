package com.example.bingo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("bingo")
public interface BingoConfig extends Config
{
    @ConfigItem(
        keyName = "rsn",
        name = "RSN",
        description = "Your RuneScape username"
    )
    default String rsn() {
        return "";
    }

    @ConfigItem(
        keyName = "authToken",
        name = "Auth Token",
        description = "JWT token for Bingo API",
        secret = true
    )
    default String authToken() {
        return "";
    }
} 