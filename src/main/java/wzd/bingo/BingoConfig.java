package wzd.bingo;

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
        keyName = "discordId",
        name = "Discord ID",
        description = "Your Discord user ID for authentication"
    )
    default String discordId() {
        return "";
    }

    @ConfigItem(
        keyName = "jwtToken",
        name = "JWT Token",
        description = "Authentication JWT token (automatically managed)",
        secret = true
    )
    default String jwtToken() {
        return "";
    }

    @ConfigItem(
        keyName = "teamId",
        name = "Team ID",
        description = "Your team ID (automatically set after login)",
        secret = true
    )
    default String teamId() {
        return "";
    }

    @ConfigItem(
        keyName = "authToken",
        name = "Auth Token",
        description = "Legacy JWT token for Bingo API (deprecated)",
        secret = true
    )
    default String authToken() {
        return "";
    }

    @ConfigItem(
        keyName = "siteUrl",
        name = "Site URL",
        description = "Base URL for the clan.bingo website"
    )
    default String siteUrl() {
        return "https://clan.bingo";
    }

    @ConfigItem(
        keyName = "authApiUrl",
        name = "Authentication API URL",
        description = "Base URL for the clan.bingo authentication API"
    )
    default String authApiUrl() {
        return "https://api.clan.bingo";
    }

    @ConfigItem(
        keyName = "isAuthenticated",
        name = "Is Authenticated",
        description = "Whether the user is currently authenticated (automatically managed)",
        hidden = true
    )
    default boolean isAuthenticated() {
        return false;
    }
} 