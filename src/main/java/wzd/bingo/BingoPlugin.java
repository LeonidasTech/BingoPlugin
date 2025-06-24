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
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import wzd.bingo.ui.AuthPanel;
import wzd.bingo.ui.BingoMainPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
    name = "Bingo",
    description = "OSRS Bingo plugin for clan.bingo integration",
    tags = {"bingo", "clan", "competition"}
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
    
    @Inject
    private BingoActivityHandler activityHandler;
    
    private NavigationButton navButton;
    private AuthPanel authPanel;
    private BingoMainPanel mainPanel;
    private PluginPanel currentPanel;

    @Override
    protected void startUp() throws Exception
    {
        log.info("Bingo plugin started");

        // Check if user is already authenticated
        if (config.isAuthenticated() && !config.jwtToken().isEmpty())
        {
            log.info("User already authenticated, initializing service for: {}", config.rsn());
            bingoService.initialize();
            showMainPanel();
        }
        else
        {
            log.info("User not authenticated, showing authentication panel");
            showAuthPanel();
        }

        // Create navigation button after panel is set
        createNavigationButton();
    }

    @Override
    protected void shutDown() throws Exception
    {
        log.info("Bingo plugin stopped");

        // Clean up resources
        if (bingoService != null)
        {
            bingoService.shutdown();
        }

        if (navButton != null)
        {
            clientToolbar.removeNavigation(navButton);
        }

        if (mainPanel != null)
        {
            mainPanel.shutdown();
        }
    }

    private void createNavigationButton()
    {
        // Ensure we have a panel set
        if (currentPanel == null)
        {
            log.warn("Cannot create navigation button - no panel set");
            return;
        }

        BufferedImage icon = null;
        try
        {
            icon = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/panel-icon.png");
            log.info("Successfully loaded panel icon from resources");
        }
        catch (Exception e)
        {
            log.warn("Could not load panel icon from resources, creating fallback: {}", e.getMessage());
            icon = createPanelIcon();
        }

        // Ensure icon is not null
        if (icon == null)
        {
            log.error("Failed to create panel icon, using minimal fallback");
            icon = createMinimalIcon();
        }

        navButton = NavigationButton.builder()
            .tooltip("Bingo")
            .icon(icon)
            .priority(5)
            .panel(currentPanel)
            .build();

        clientToolbar.addNavigation(navButton);
        log.info("Navigation button created successfully");
    }

    private void showAuthPanel()
    {
        if (authPanel == null)
        {
            authPanel = new AuthPanel(config, bingoService, client, this::onAuthenticationSuccess);
        }
        
        switchToPanel(authPanel);
    }

    private void showMainPanel()
    {
        if (mainPanel == null)
        {
            mainPanel = new BingoMainPanel(config, bingoService, configManager, activityHandler, this::onLogout);
        }
        
        switchToPanel(mainPanel);
    }

    private void switchToPanel(PluginPanel newPanel)
    {
        currentPanel = newPanel;
        
        if (navButton != null)
        {
            // Remove old navigation button
            clientToolbar.removeNavigation(navButton);
            
            // Create new navigation button with updated panel
            BufferedImage icon = null;
            try
            {
                icon = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/panel-icon.png");
            }
            catch (Exception e)
            {
                log.warn("Could not load panel icon, using fallback: {}", e.getMessage());
                icon = createPanelIcon();
            }

            // Ensure icon is not null
            if (icon == null)
            {
                icon = createMinimalIcon();
            }

            navButton = NavigationButton.builder()
                .tooltip("Bingo")
                .icon(icon)
                .priority(5)
                .panel(currentPanel)
                .build();
            
            // Add new navigation button
            clientToolbar.addNavigation(navButton);
        }
    }

    private void onAuthenticationSuccess()
    {
        log.info("Authentication successful, switching to main panel");
        
        // Initialize service for authenticated user
        bingoService.initialize();
        
        // Switch to main panel
        showMainPanel();
    }

    private void onLogout()
    {
        log.info("User logged out, switching to authentication panel");
        
        // Clean up main panel
        if (mainPanel != null)
        {
            mainPanel.shutdown();
            mainPanel = null;
        }
        
        // Switch to auth panel
        showAuthPanel();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged)
    {
        // Update RSN in auth panel when player logs in/out
        if (authPanel != null)
        {
            authPanel.onGameStateChanged();
        }
    }

    private BufferedImage createMinimalIcon()
    {
        try
        {
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = fallback.createGraphics();
            g.setColor(new java.awt.Color(0, 112, 192));
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            log.info("Created minimal fallback icon");
            return fallback;
        }
        catch (Exception e)
        {
            log.error("Failed to create minimal icon", e);
            return null;
        }
    }

    private BufferedImage createPanelIcon()
    {
        try
        {
            BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g2d = icon.createGraphics();
            
            // Enable antialiasing for smoother text
            g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Set blue background
            g2d.setColor(new java.awt.Color(0, 112, 192));
            g2d.fillRect(0, 0, 16, 16);
            
            // Draw white "B" text
            g2d.setColor(java.awt.Color.WHITE);
            g2d.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
            java.awt.FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth("B");
            int textHeight = fm.getAscent();
            int x = (16 - textWidth) / 2;
            int y = (16 + textHeight) / 2 - 1;
            g2d.drawString("B", x, y);
            
            g2d.dispose();
            return icon;
        }
        catch (Exception e)
        {
            log.error("Failed to create fallback panel icon", e);
            // Return a simple colored square as last resort
            BufferedImage fallback = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = fallback.createGraphics();
            g.setColor(new java.awt.Color(0, 112, 192));
            g.fillRect(0, 0, 16, 16);
            g.dispose();
            return fallback;
        }
    }

    @Provides
    BingoConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BingoConfig.class);
    }
} 