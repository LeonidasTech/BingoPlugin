package wzd.bingo;

import wzd.bingo.ui.AuthPanel;
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
import java.awt.*;

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
        
        // Create the auth panel with client for RSN detection
        authPanel = new AuthPanel(bingoService, config, configManager, client);
        
        // Try to load the icon from resources/wzd/bingo/
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/panel-icon.png");
        
        // If icon loading failed, create a simple fallback icon
        if (icon == null)
        {
            log.warn("Could not load /wzd/bingo/panel-icon.png, using fallback icon");
            icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = icon.createGraphics();
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 16, 16);
            g.setColor(Color.WHITE);
            g.drawString("B", 6, 12);
            g.dispose();
        }
        
        // Create navigation button for the side panel
        navButton = NavigationButton.builder()
            .tooltip("Bingo")
            .icon(icon)
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
            // Player logged in - update RSN field in auth panel
            if (authPanel != null)
            {
                authPanel.onGameStateChanged();
            }
            
            if (bingoService.isAuthenticated())
            {
                log.info("Player logged in, Bingo service is authenticated");
                // TODO: Sync current location, skills, etc. with bingo service
            }
        }
        else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
        {
            // Player logged out - update RSN field in auth panel
            if (authPanel != null)
            {
                authPanel.onGameStateChanged();
            }
            log.info("Player logged out");
        }
    }

    @Provides
    BingoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BingoConfig.class);
    }
} 