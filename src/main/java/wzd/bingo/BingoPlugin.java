package wzd.bingo;

import com.google.inject.Provides;
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
import wzd.bingo.ui.AuthPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Bingo",
    description = "OSRS Bingo game integration with clan.bingo",
    tags = {"bingo", "competition", "team", "challenges"}
)
public class BingoPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private BingoConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private BingoService bingoService;

    private NavigationButton navButton;
    private AuthPanel authPanel;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Bingo plugin started");
        
        // Create the authentication panel
        authPanel = new AuthPanel(config, bingoService, client);
        
        // Create navigation button with icon
        BufferedImage icon = createPanelIcon();
        navButton = NavigationButton.builder()
            .tooltip("Bingo")
            .icon(icon)
            .priority(10)
            .panel(authPanel)
            .build();
        
        clientToolbar.addNavigation(navButton);
        
        // Initialize service if already authenticated
        if (bingoService.isAuthenticated())
        {
            bingoService.initialize();
        }
        
        log.info("Bingo plugin navigation button added");
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Bingo plugin shutting down");
        
        // Clean up navigation button
        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }
        
        // Shutdown services
        if (bingoService != null)
        {
            bingoService.shutdown();
        }
        
        // Shutdown auth panel
        if (authPanel != null)
        {
            authPanel.shutdown();
        }
        
        log.info("Bingo plugin shutdown complete");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        // Update auth panel when game state changes (login/logout)
        if (authPanel != null)
        {
            authPanel.onGameStateChanged();
        }
        
        // Start/stop services based on login state
        GameState gameState = gameStateChanged.getGameState();
        if (gameState == GameState.LOGGED_IN)
        {
            // If authenticated, ensure services are running
            if (bingoService.isAuthenticated())
            {
                SwingUtilities.invokeLater(() -> {
                    bingoService.startHeartbeat();
                });
            }
        }
        else if (gameState == GameState.LOGIN_SCREEN || gameState == GameState.CONNECTION_LOST)
        {
            // Stop heartbeat when logged out
            SwingUtilities.invokeLater(() -> {
                bingoService.stopHeartbeat();
            });
        }
        
        log.debug("Game state changed: {}", gameState);
    }

    /**
     * Create the panel icon for the navigation button
     */
    private BufferedImage createPanelIcon()
    {
        try
        {
            // Try to load the panel icon from resources
            BufferedImage loadedIcon = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/panel-icon.png");
            if (loadedIcon != null)
            {
                return loadedIcon;
            }
        }
        catch (Exception e)
        {
            log.warn("Could not load panel-icon.png from resources: {}", e.getMessage());
        }
        
        // Create fallback icon if loading failed
        log.info("Creating fallback 'B' icon");
        return createFallbackIcon();
    }
    
    /**
     * Create a fallback icon when the resource icon cannot be loaded
     */
    private BufferedImage createFallbackIcon()
    {
        try
        {
            // Create a simple fallback icon - blue "B"
            BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = icon.createGraphics();
            
            // Enable antialiasing for better text rendering
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            
            // Fill background with transparent
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, 16, 16);
            g2d.setComposite(AlphaComposite.SrcOver);
            
            // Draw blue "B"
            g2d.setColor(new Color(0, 112, 192));
            g2d.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (16 - fm.stringWidth("B")) / 2;
            int y = (16 - fm.getHeight()) / 2 + fm.getAscent();
            g2d.drawString("B", x, y);
            
            g2d.dispose();
            
            log.info("Successfully created fallback 'B' icon");
            return icon;
        }
        catch (Exception e)
        {
            log.error("Failed to create fallback icon, creating minimal icon", e);
            
            // Last resort - create a minimal solid color icon
            BufferedImage minimalIcon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = minimalIcon.createGraphics();
            g.setColor(new Color(0, 112, 192));
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            
            return minimalIcon;
        }
    }

    @Provides
    BingoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BingoConfig.class);
    }
} 