package com.example.bingo;

import com.example.bingo.ui.AuthPanel;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Bingo",
    description = "OSRS Bingo game plugin with team collaboration features"
)
public class BingoPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private BingoConfig config;

    @Inject
    private BingoService bingoService;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    private AuthPanel authPanel;
    private NavigationButton navButton;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Bingo plugin started!");
        
        // Create the auth panel
        authPanel = new AuthPanel(bingoService, configManager);

        // Create navigation button for the side panel
        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel-icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Bingo")
            .icon(icon != null ? icon : new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB))
            .priority(5)
            .panel(authPanel)
            .build();
        
        clientToolbar.addNavigation(navButton);
        
        // If already authenticated, initialize the service
        if (bingoService.isAuthenticated())
        {
            bingoService.initialize();
        }
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Bingo plugin stopped!");
        
        // Remove the navigation button
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
        {
            // Player logged in - could sync some game state if needed
            if (bingoService.isAuthenticated())
            {
                log.info("Player logged in, Bingo service is authenticated");
                // TODO: Sync current location, skills, etc. with bingo service
            }
        }
        else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Player logged out
            log.info("Player logged out");
        }
    }

    @Provides
    BingoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BingoConfig.class);
    }
} 