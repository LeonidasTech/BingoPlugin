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
        keyName = "authToken",
        name = "Auth Token",
        description = "JWT token for Bingo API",
        secret = true
    )
    default String authToken() {
        return "";
    }

    @ConfigItem(
        keyName = "profileUrl",
        name = "Profile URL",
        description = "URL for the clan.bingo profile page where users can manage authentication tokens"
    )
    default String profileUrl() {
        return "https://clan.bingo/account/profile";
    }

    @ConfigItem(
        keyName = "authApiUrl",
        name = "Authentication API URL",
        description = "Base URL for the clan.bingo authentication API"
    )
    default String authApiUrl() {
        return "https://api.clan.bingo";
    }
} 