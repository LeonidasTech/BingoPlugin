package wzd.bingo.ui;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import wzd.bingo.BingoConfig;
import wzd.bingo.BingoService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Slf4j
public class AuthPanel extends PluginPanel
{
    private static final Color HOVER_COLOR = ColorScheme.BRAND_ORANGE;
    private static final Color BUTTON_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color SUCCESS_COLOR = new Color(0, 150, 0);
    private static final Color ERROR_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;

    private final BingoConfig config;
    private final BingoService bingoService;
    private final Client client;
    
    private JTextField rsnField;
    private JTextField discordIdField;
    private JButton linkAccountButton;
    private JButton visitProfileButton;
    private JLabel statusLabel;
    private JLabel instructionsLabel;
    private Timer rsnUpdateTimer;
    
    public AuthPanel(BingoConfig config, BingoService bingoService, Client client)
    {
        this.config = config;
        this.bingoService = bingoService;
        this.client = client;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        // Start timer to update RSN field
        startRsnUpdateTimer();
        
        // Initial RSN update
        updateRsnField();
        
        log.info("AuthPanel initialized with clan.bingo branding");
    }
    
    private void initializeComponents()
    {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // RSN field (read-only, auto-detected)
        rsnField = new JTextField();
        rsnField.setEditable(false);
        rsnField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rsnField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rsnField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        rsnField.setFont(rsnField.getFont().deriveFont(13f));
        
        // Discord ID field
        discordIdField = new JTextField();
        discordIdField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        discordIdField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        discordIdField.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        discordIdField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        discordIdField.setFont(discordIdField.getFont().deriveFont(13f));
        
        // Pre-fill Discord ID if available
        if (config.discordId() != null && !config.discordId().isEmpty())
        {
            discordIdField.setText(config.discordId());
        }
        
        // Link Account button
        linkAccountButton = createStyledButton("Link Account");
        
        // Visit Profile button
        visitProfileButton = createStyledButton("Visit Profile");
        
        // Status label
        statusLabel = new JLabel("Ready to authenticate");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(12f));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Instructions label with better spacing
        instructionsLabel = new JLabel("<html><div style='text-align: left; font-size: 12px; color: #FFA500; line-height: 1.6;'>" +
            "<b>How to get your Discord ID:</b><br/><br/>" +
            "1. Log into clan.bingo<br/>" +
            "2. Account → View Profile<br/>" +
            "3. Your Discord ID is displayed there<br/>" +
            "4. Copy and paste it above" +
            "</div></html>");
        instructionsLabel.setForeground(new Color(255, 165, 0)); // Orange color
        instructionsLabel.setHorizontalAlignment(SwingConstants.LEFT);
    }
    
    private JButton createStyledButton(String text)
    {
        JButton button = new JButton(text);
        button.setBackground(BUTTON_COLOR);
        button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(100, 32));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                button.setBackground(HOVER_COLOR);
            }
            
            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(BUTTON_COLOR);
            }
        });
        
        return button;
    }
    
    private void setupLayout()
    {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(15, 15, 15, 15));
        
        // Header panel with logo
        JPanel headerPanel = createHeaderPanel();
        
        // Main content panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // RSN section
        JLabel rsnTitleLabel = new JLabel("RuneScape Name:");
        rsnTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rsnTitleLabel.setFont(rsnTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        rsnTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel rsnSubLabel = new JLabel("(Auto-detected when logged in)");
        rsnSubLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
        rsnSubLabel.setFont(rsnSubLabel.getFont().deriveFont(11f));
        rsnSubLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        rsnField.setAlignmentX(Component.LEFT_ALIGNMENT);
        rsnField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        
        // Discord ID section
        JLabel discordIdTitleLabel = new JLabel("Discord ID:");
        discordIdTitleLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        discordIdTitleLabel.setFont(discordIdTitleLabel.getFont().deriveFont(Font.BOLD, 13f));
        discordIdTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        discordIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
        discordIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        
        // Instructions panel
        instructionsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.add(linkAccountButton);
        buttonPanel.add(visitProfileButton);
        
        // Status label
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Add all components with proper spacing
        mainPanel.add(Box.createVerticalStrut(12));
        mainPanel.add(rsnTitleLabel);
        mainPanel.add(Box.createVerticalStrut(6));
        mainPanel.add(rsnSubLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(rsnField);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(discordIdTitleLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(discordIdField);
        mainPanel.add(Box.createVerticalStrut(18));
        mainPanel.add(instructionsLabel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(buttonPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(statusLabel);
        mainPanel.add(Box.createVerticalGlue());
        
        add(headerPanel, BorderLayout.NORTH);
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private JPanel createHeaderPanel()
    {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(0, 112, 192)),
            BorderFactory.createEmptyBorder(12, 10, 12, 10)
        ));
        
        try
        {
            // Load clan.bingo header image
            ImageIcon headerIcon = new ImageIcon(ImageUtil.loadImageResource(getClass(), "/wzd/bingo/clan-bingo-header.png"));
            JLabel headerLabel = new JLabel(headerIcon);
            headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
            headerLabel.setForeground(new Color(0, 112, 192));
            headerPanel.add(headerLabel, BorderLayout.CENTER);
        }
        catch (Exception e)
        {
            // Fallback text header
            JLabel headerLabel = new JLabel("clan.bingo authentication");
            headerLabel.setForeground(new Color(0, 112, 192));
            headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
            headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
            headerPanel.add(headerLabel, BorderLayout.CENTER);
            log.warn("Could not load header image, using text fallback", e);
        }
        
        return headerPanel;
    }
    
    private void setupEventHandlers()
    {
        linkAccountButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                handleLinkAccount();
            }
        });
        
        visitProfileButton.addActionListener(new ActionListener()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                handleVisitProfile();
            }
        });
    }
    
    private void handleLinkAccount()
    {
        String rsn = rsnField.getText().trim();
        String discordId = discordIdField.getText().trim();
        
        if (rsn.isEmpty() || rsn.equals("(Must be logged in first)"))
        {
            updateStatus("Please log into OSRS first", ERROR_COLOR);
            return;
        }
        
        if (discordId.isEmpty())
        {
            updateStatus("Please enter your Discord ID", ERROR_COLOR);
            return;
        }
        
        // Validate Discord ID format (should be numeric and 17-19 digits)
        if (!discordId.matches("\\d{17,19}"))
        {
            updateStatus("Invalid Discord ID format", ERROR_COLOR);
            return;
        }
        
        linkAccountButton.setText("Linking...");
        linkAccountButton.setEnabled(false);
        updateStatus("Authenticating with clan.bingo...", ColorScheme.LIGHT_GRAY_COLOR);
        
        // Perform authentication in background thread (not on EDT)
        new Thread(() -> {
            Optional<String> result = bingoService.authenticateWithDiscord(rsn, discordId);
            
            // Update UI back on EDT
            SwingUtilities.invokeLater(() -> {
                linkAccountButton.setText("Link Account");
                linkAccountButton.setEnabled(true);
                
                if (result.isPresent())
                {
                    updateStatus("Authentication successful!", SUCCESS_COLOR);
                    updateUIAfterAuthentication();
                    
                    // Initialize the bingo service
                    bingoService.initialize();
                }
                else
                {
                    updateStatus("Authentication failed. Check your Discord ID.", ERROR_COLOR);
                }
            });
        }).start();
    }
    
    private void handleVisitProfile()
    {
        try
        {
            Desktop.getDesktop().browse(URI.create(config.profileUrl()));
            log.info("Opened profile URL: {}", config.profileUrl());
        }
        catch (IOException e)
        {
            log.error("Failed to open profile URL", e);
            updateStatus("Failed to open profile page", ERROR_COLOR);
        }
    }
    
    private void updateStatus(String message, Color color)
    {
        statusLabel.setText(message);
        statusLabel.setForeground(color);
        log.info("Status updated: {}", message);
    }
    
    private void updateUIAfterAuthentication()
    {
        // Update panel background to indicate success
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Update instructions
        instructionsLabel.setText("<html><div style='text-align: center; font-size: 11px;'>" +
            "✓ Account linked successfully<br/>" +
            "Visit profile to manage settings" +
            "</div></html>");
        instructionsLabel.setForeground(SUCCESS_COLOR.brighter());
    }
    
    private void startRsnUpdateTimer()
    {
        rsnUpdateTimer = new Timer(2000, e -> updateRsnField()); // Update every 2 seconds
        rsnUpdateTimer.start();
    }
    
    public void updateRsnField()
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer != null && localPlayer.getName() != null)
        {
            String currentRsn = localPlayer.getName();
            if (!currentRsn.equals(rsnField.getText()))
            {
                rsnField.setText(currentRsn);
                rsnField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                
                // Add subtle green tint to indicate logged in
                Color loggedInColor = new Color(
                    ColorScheme.DARKER_GRAY_COLOR.getRed(),
                    Math.min(255, ColorScheme.DARKER_GRAY_COLOR.getGreen() + 20),
                    ColorScheme.DARKER_GRAY_COLOR.getBlue()
                );
                rsnField.setBackground(loggedInColor);
                
                log.debug("RSN field updated: {}", currentRsn);
            }
        }
        else
        {
            if (!rsnField.getText().equals("(Must be logged in first)"))
            {
                rsnField.setText("(Must be logged in first)");
                
                // Add subtle red tint to indicate not logged in
                Color notLoggedInColor = new Color(
                    Math.min(255, ColorScheme.DARKER_GRAY_COLOR.getRed() + 20),
                    ColorScheme.DARKER_GRAY_COLOR.getGreen(),
                    ColorScheme.DARKER_GRAY_COLOR.getBlue()
                );
                rsnField.setBackground(notLoggedInColor);
            }
        }
    }
    
    public void onGameStateChanged()
    {
        // Immediately update RSN field when game state changes
        updateRsnField();
    }
    
    public void shutdown()
    {
        if (rsnUpdateTimer != null)
        {
            rsnUpdateTimer.stop();
        }
        log.info("AuthPanel shutdown complete");
    }
} 