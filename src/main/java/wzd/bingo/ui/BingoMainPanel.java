package wzd.bingo.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class BingoMainPanel extends PluginPanel
{
    // Enhanced color scheme
    private static final Color ACCENT_COLOR = new Color(0, 150, 200);
    private static final Color HOVER_COLOR = ColorScheme.BRAND_ORANGE;
    private static final Color BUTTON_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color BUTTON_HOVER_COLOR = new Color(80, 80, 80);
    private static final Color SUCCESS_COLOR = new Color(0, 180, 0);
    private static final Color ERROR_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;
    private static final Color INFO_PANEL_COLOR = new Color(45, 45, 45);
    private static final Color CARD_BORDER_COLOR = new Color(70, 70, 70);

    private final BingoConfig config;
    private final BingoService bingoService;
    private final ConfigManager configManager;
    private final Runnable onLogout;
    
    private JComboBox<EventItem> eventDropdown;
    private JPanel eventInfoPanel;
    private JLabel totalParticipantsLabel;
    private JLabel eventNameLabel;
    private JLabel prizePoolLabel;
    private JLabel timeRemainingLabel;
    private JLabel totalTilesLabel;
    private JButton viewBoardButton;
    private JLabel statusLabel;
    private Timer eventRefreshTimer;
    
    // Icon buttons
    private JButton viewProfileButton;
    private JButton logoutButton;
    
    public BingoMainPanel(BingoConfig config, BingoService bingoService, ConfigManager configManager, Runnable onLogout)
    {
        this.config = config;
        this.bingoService = bingoService;
        this.configManager = configManager;
        this.onLogout = onLogout;
        
        // Set up JWT expiration callback for automatic logout
        bingoService.setJwtExpirationCallback(() -> {
            log.info("JWT expired - automatically logging out user");
            updateStatus("Session expired - please log in again", ERROR_COLOR);
            
            // Trigger logout to switch back to auth panel
            if (onLogout != null)
            {
                onLogout.run();
            }
        });
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        // Start periodic event refresh (every 5 minutes)
        startEventRefreshTimer();
        
        // Load initial data
        refreshActiveEvents();
        
        log.info("BingoMainPanel initialized");
    }
    
    @Override
    public Insets getInsets()
    {
        return new Insets(0, 0, 0, 0); // Remove any default insets
    }
    
    private void initializeComponents()
    {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Icon-based buttons with 25% width each
        viewProfileButton = createIconButton("👤", "Profile", ACCENT_COLOR);
        logoutButton = createIconButton("🚪", "Logout", new Color(180, 50, 50));
        
        // Enhanced event dropdown with better styling
        eventDropdown = new JComboBox<>();
        eventDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        eventDropdown.setForeground(Color.WHITE);
        eventDropdown.setFont(eventDropdown.getFont().deriveFont(Font.BOLD, 13f));
        eventDropdown.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 2),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        eventDropdown.setRenderer(new EventItemRenderer());
        eventDropdown.setFocusable(true);
        
        // Enhanced event info labels (remove event name as it will be header)
        totalParticipantsLabel = createStyledInfoLabel("Participants: -");
        prizePoolLabel = createStyledInfoLabel("Prize Pool: -");
        timeRemainingLabel = createStyledInfoLabel("Time Remaining: -");
        totalTilesLabel = createStyledInfoLabel("Total Tiles: -");
        
        // Enhanced View Board button
        viewBoardButton = createPrimaryButton("View Bingo Board");
        viewBoardButton.setEnabled(false); // Disable by default until event is selected
        
        // Enhanced status label
        statusLabel = new JLabel("Loading events...");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 12f));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }
    
    private JLabel createStyledInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(13f));
        return label;
    }
    
    private JButton createIconButton(String icon, String tooltip, Color backgroundColor)
    {
        JButton button = new JButton(icon);
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 16f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        button.setToolTipText(tooltip);
        
        // Enhanced hover effect
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(backgroundColor.brighter());
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(backgroundColor);
            }
        });
        
        return button;
    }
    
    private JButton createImageIconButton(String imageName, String tooltip, Color backgroundColor)
    {
        JButton button = new JButton();
        
        try
        {
            // Load the image from resources
            ImageIcon icon = new ImageIcon(ImageUtil.loadImageResource(getClass(), imageName));
            // Scale the image to fit the button
            Image scaledImage = icon.getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            button.setIcon(new ImageIcon(scaledImage));
        }
        catch (Exception e)
        {
            log.warn("Failed to load image: {}, falling back to text", imageName);
            button.setText("DC"); // Fallback text for Discord
        }
        
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        button.setToolTipText(tooltip);
        
        // Enhanced hover effect
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(backgroundColor.brighter());
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e)
            {
                button.setBackground(backgroundColor);
            }
        });
        
        return button;
    }
    
    private JButton createPrimaryButton(String text)
    {
        JButton button = new JButton(text);
        button.setBackground(ACCENT_COLOR);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(160, 40));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        
        // Primary button hover effect
        button.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(ACCENT_COLOR.brighter());
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e)
            {
                if (button.isEnabled())
                {
                    button.setBackground(ACCENT_COLOR);
                }
                else
                {
                    button.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
                }
            }
        });
        
        return button;
    }
    
    private void setupLayout()
    {
        setLayout(new BorderLayout());
        setBorder(null); // Remove all borders
        
        // Main content panel with proper full-width layout
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainPanel.setBorder(new EmptyBorder(5, 5, 5, 5)); // Minimal border
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.insets = new Insets(0, 0, 8, 0);
        

        // Icon buttons panel with 25% width each
        JPanel iconButtonsPanel = createIconButtonsPanel();
        gbc.gridy = 0;
        mainPanel.add(iconButtonsPanel, gbc);
        
        // Event selection card (full width)
        JPanel eventSelectionCard = createEventSelectionCard();
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 8, 0);
        mainPanel.add(eventSelectionCard, gbc);
        
        // Event info card (initially hidden)
        eventInfoPanel = createEventInfoCard();
        eventInfoPanel.setVisible(false); // Hide by default
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 10, 0);
        mainPanel.add(eventInfoPanel, gbc);
        
        // Action buttons panel
        JPanel actionPanel = createActionPanel();
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 8, 0);
        mainPanel.add(actionPanel, gbc);
        
        // Status panel
        JPanel statusPanelBottom = createStatusPanel();
        gbc.gridy = 4;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        mainPanel.add(statusPanelBottom, gbc);
        
        add(mainPanel, BorderLayout.CENTER);
    }
    
    private JPanel createIconButtonsPanel()
    {
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 2, 0)); // 4 buttons, 25% each
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setPreferredSize(new Dimension(0, 40)); // Remove max width constraint
        
        // Create all 4 buttons
        JButton discordButton = createImageIconButton("discord.png", "Discord", new Color(114, 137, 218));
        JButton websiteButton = createIconButton("🌐", "Website", BUTTON_COLOR);
        
        // Add buttons - each takes 25% width
        buttonPanel.add(discordButton);
        buttonPanel.add(websiteButton);
        buttonPanel.add(viewProfileButton);
        buttonPanel.add(logoutButton);
        
        // Set up event handlers for new buttons
        discordButton.addActionListener(e -> openDiscord());
        websiteButton.addActionListener(e -> openWebsite());
        
        return buttonPanel;
    }
    
    private JPanel createEventSelectionCard()
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(INFO_PANEL_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        // Card title
        JLabel titleLabel = new JLabel("Active Events");
        titleLabel.setForeground(ACCENT_COLOR);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        // Dropdown with reduced padding
        eventDropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        
        card.add(titleLabel);
        card.add(Box.createVerticalStrut(6));
        card.add(eventDropdown);
        
        return card;
    }
    
    private JPanel createEventInfoCard()
    {
        JPanel infoCard = new JPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setBackground(INFO_PANEL_COLOR);
        infoCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        // Dynamic card title (will be updated with event name)
        eventNameLabel = new JLabel("Event Details");
        eventNameLabel.setForeground(ACCENT_COLOR);
        eventNameLabel.setFont(eventNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        eventNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        eventNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Info grid with reduced items (no event name anymore)
        JPanel infoGrid = new JPanel(new GridLayout(4, 1, 0, 6));
        infoGrid.setBackground(INFO_PANEL_COLOR);
        infoGrid.add(totalParticipantsLabel);
        infoGrid.add(prizePoolLabel);
        infoGrid.add(timeRemainingLabel);
        infoGrid.add(totalTilesLabel);
        
        infoCard.add(eventNameLabel);
        infoCard.add(Box.createVerticalStrut(8));
        infoCard.add(infoGrid);
        
        return infoCard;
    }
    
    private JPanel createActionPanel()
    {
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        actionPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        actionPanel.add(viewBoardButton);
        return actionPanel;
    }
    
    private JPanel createStatusPanel()
    {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        
        return statusPanel;
    }
    
    private void setupEventHandlers()
    {
        // Profile and logout buttons are handled in createIconButtonsPanel
        // Discord and website buttons are also handled there
        
        // Event dropdown selection
        eventDropdown.addActionListener(e -> {
            EventItem selectedEvent = (EventItem) eventDropdown.getSelectedItem();
            if (selectedEvent != null && !selectedEvent.isEmpty())
            {
                updateEventInfo(selectedEvent);
                showEventDetails(true);
                viewBoardButton.setEnabled(true);
            }
            else
            {
                clearEventInfo();
                showEventDetails(false);
                viewBoardButton.setEnabled(false);
            }
        });
        
        // View Board button
        viewBoardButton.addActionListener(e -> {
            EventItem selectedEvent = (EventItem) eventDropdown.getSelectedItem();
            if (selectedEvent != null && !selectedEvent.isEmpty())
            {
                String boardUrl = config.siteUrl() + "/event/" + selectedEvent.getBingoId();
                try
                {
                    Desktop.getDesktop().browse(URI.create(boardUrl));
                    log.info("Opened board URL: {}", boardUrl);
                }
                catch (IOException ex)
                {
                    log.error("Failed to open board URL", ex);
                    updateStatus("Failed to open board page", ERROR_COLOR);
                }
            }
        });
        
        // Profile button handler
        viewProfileButton.addActionListener(e -> openProfilePage());
        
        // Logout button handler  
        logoutButton.addActionListener(e -> handleLogout());
    }
    
    private void showEventDetails(boolean show)
    {
        if (eventInfoPanel != null)
        {
            eventInfoPanel.setVisible(show);
            eventInfoPanel.revalidate();
            eventInfoPanel.repaint();
            
            // Also revalidate the parent container
            if (eventInfoPanel.getParent() != null)
            {
                eventInfoPanel.getParent().revalidate();
                eventInfoPanel.getParent().repaint();
            }
        }
    }
    
    private void openProfilePage()
    {
        try
        {
            String profileUrl = config.siteUrl() + "/profile";
            Desktop.getDesktop().browse(URI.create(profileUrl));
            log.info("Opened profile URL: {}", profileUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to open profile URL", e);
            updateStatus("Failed to open profile page", ERROR_COLOR);
        }
    }
    
    private void handleLogout()
    {
        // Clear authentication data
        configManager.setConfiguration("bingo", "jwtToken", "");
        configManager.setConfiguration("bingo", "isAuthenticated", false);
        
        // Stop services
        bingoService.shutdown();
        
        // Notify parent to switch back to auth panel
        if (onLogout != null)
        {
            onLogout.run();
        }
        
        log.info("User logged out successfully");
    }
    
    private void refreshActiveEvents()
    {
        updateStatus("Loading events...", ColorScheme.LIGHT_GRAY_COLOR);
        
        // Disable interactions while loading
        eventDropdown.setEnabled(false);
        viewBoardButton.setEnabled(false);
        showEventDetails(false);
        
        new Thread(() -> {
            Optional<JsonObject> eventsData = bingoService.fetchActiveEvents();
            
            SwingUtilities.invokeLater(() -> {
                // Re-enable interactions
                eventDropdown.setEnabled(true);
                
                if (eventsData.isPresent())
                {
                    updateEventDropdown(eventsData.get());
                    updateStatus("Events loaded successfully", SUCCESS_COLOR);
                }
                else
                {
                    updateStatus("No events available or connection failed", ERROR_COLOR);
                    eventDropdown.removeAllItems();
                    eventDropdown.addItem(new EventItem("", "No events available", "", 0, 0, 0, false, "", 0));
                    showEventDetails(false);
                }
            });
        }).start();
    }
    
    private void updateEventDropdown(JsonObject eventsData)
    {
        eventDropdown.removeAllItems();
        
        // Add placeholder item first
        eventDropdown.addItem(new EventItem("", "Select an event...", "", 0, 0, 0, false, "", 0));
        
        try
        {
            // Check if this is the expected wrapper format
            boolean hasActiveEvent = eventsData.has("hasActiveEvent") && eventsData.get("hasActiveEvent").getAsBoolean();
            
            if (hasActiveEvent && eventsData.has("activeEvents"))
            {
                JsonArray activeEvents = eventsData.getAsJsonArray("activeEvents");
                parseEventArray(activeEvents);
            }
            // Check if the response is directly an array of events
            else if (eventsData.has("events"))
            {
                JsonArray events = eventsData.getAsJsonArray("events");
                parseEventArray(events);
            }
            // Handle case where the entire response might be an array
            else
            {
                // Try to see if we can find events in the root object
                for (String key : eventsData.keySet())
                {
                    JsonElement element = eventsData.get(key);
                    if (element.isJsonArray())
                    {
                        parseEventArray(element.getAsJsonArray());
                        break;
                    }
                }
            }
            
            // Keep placeholder selected (no event selected by default)
            eventDropdown.setSelectedIndex(0);
            showEventDetails(false);
        }
        catch (Exception e)
        {
            log.error("Error processing events data", e);
            eventDropdown.removeAllItems();
            eventDropdown.addItem(new EventItem("", "Error loading events", "", 0, 0, 0, false, "", 0));
            showEventDetails(false);
        }
    }
    
    private void parseEventArray(JsonArray events)
    {
        for (JsonElement eventElement : events)
        {
            JsonObject event = eventElement.getAsJsonObject();
            
            // Handle both possible field name formats
            String bingoId = getStringField(event, "bingoId", "bingoid");
            String name = getStringField(event, "name", "name");
            String groupId = getStringField(event, "groupId", "groupid");
            int durationDays = getIntField(event, "durationDays", "durationdays");
            boolean isActive = getBooleanField(event, "isActive", "isactive");
            String prizePool = getStringField(event, "prizePool", "prizepool");
            // Also try other possible field names for prize pool
            if (prizePool.isEmpty()) {
                prizePool = getStringField(event, "prize_pool", "prize");
            }
            if (prizePool.isEmpty() && event.has("prizeAmount")) {
                prizePool = event.get("prizeAmount").getAsString();
            }
            if (prizePool.isEmpty() && event.has("reward")) {
                prizePool = event.get("reward").getAsString();
            }
            
            // Get days remaining directly from API response, or calculate if not available
            int daysRemaining = getIntField(event, "daysRemaining", "daysremaining", durationDays);
            if (daysRemaining == durationDays && event.has("createdat") && durationDays > 0)
            {
                // Fallback calculation if daysRemaining not provided
                daysRemaining = Math.max(0, durationDays - 1); // Simplified for now
            }
            
            int totalTiles = getIntField(event, "totalTiles", "totaltiles", 25); // Default to 25
            int participants = getIntField(event, "participants", "participants", 0); // Default to 0
            
            EventItem eventItem = new EventItem(bingoId, name, groupId, durationDays, daysRemaining, totalTiles, isActive, prizePool, participants);
            eventDropdown.addItem(eventItem);
            log.info("Added event to dropdown: {} (Active: {})", name, isActive);
        }
    }
    
    private String getStringField(JsonObject obj, String primaryKey, String fallbackKey)
    {
        if (obj.has(primaryKey))
        {
            return obj.get(primaryKey).getAsString();
        }
        else if (obj.has(fallbackKey))
        {
            return obj.get(fallbackKey).getAsString();
        }
        return "";
    }
    
    private int getIntField(JsonObject obj, String primaryKey, String fallbackKey)
    {
        return getIntField(obj, primaryKey, fallbackKey, 0);
    }
    
    private int getIntField(JsonObject obj, String primaryKey, String fallbackKey, int defaultValue)
    {
        if (obj.has(primaryKey))
        {
            return obj.get(primaryKey).getAsInt();
        }
        else if (obj.has(fallbackKey))
        {
            return obj.get(fallbackKey).getAsInt();
        }
        return defaultValue;
    }
    
    private boolean getBooleanField(JsonObject obj, String primaryKey, String fallbackKey)
    {
        if (obj.has(primaryKey))
        {
            return obj.get(primaryKey).getAsBoolean();
        }
        else if (obj.has(fallbackKey))
        {
            return obj.get(fallbackKey).getAsBoolean();
        }
        return true; // Default to active
    }
    
    private void updateEventInfo(EventItem event)
    {
        // Use event name as the header
        eventNameLabel.setText(event.getName());
        eventNameLabel.setForeground(ACCENT_COLOR);
        eventNameLabel.setFont(eventNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        
        totalParticipantsLabel.setText("👥 Participants: " + event.getParticipants());
        String prizeText = event.getPrizePool();
        if (prizeText == null || prizeText.isEmpty()) {
            prizeText = "To be announced";
        }
        prizePoolLabel.setText("💰 Prize Pool: " + prizeText);
        timeRemainingLabel.setText("⏰ Days Remaining: " + event.getDaysRemaining());
        totalTilesLabel.setText("🎯 Total Tiles: " + event.getTotalTiles());
        
        // Enable the view board button
        viewBoardButton.setEnabled(true);
        viewBoardButton.setBackground(ACCENT_COLOR);
    }
    
    private void clearEventInfo()
    {
        eventNameLabel.setText("Event Details");
        eventNameLabel.setForeground(ACCENT_COLOR);
        
        totalParticipantsLabel.setText("👥 Participants: -");
        prizePoolLabel.setText("💰 Prize Pool: -");
        timeRemainingLabel.setText("⏰ Time Remaining: -");
        totalTilesLabel.setText("🎯 Total Tiles: -");
        
        // Disable the view board button
        viewBoardButton.setEnabled(false);
        showEventDetails(false);
    }
    
    private void updateStatus(String message, Color color)
    {
        statusLabel.setText("ℹ️ " + message);
        statusLabel.setForeground(color);
    }
    
    private void startEventRefreshTimer()
    {
        // Refresh events every 5 minutes
        eventRefreshTimer = new Timer(5 * 60 * 1000, e -> refreshActiveEvents());
        eventRefreshTimer.start();
    }
    
    public void shutdown()
    {
        if (eventRefreshTimer != null)
        {
            eventRefreshTimer.stop();
        }
        log.info("BingoMainPanel shutdown complete");
    }
    
    private void openDiscord()
    {
        try
        {
            // Open Discord invite or server link
            String discordUrl = "https://discord.gg/clanbingo"; // Replace with actual Discord invite
            Desktop.getDesktop().browse(URI.create(discordUrl));
            log.info("Opened Discord URL: {}", discordUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to open Discord URL", e);
            updateStatus("Failed to open Discord", ERROR_COLOR);
        }
    }
    
    private void openWebsite()
    {
        try
        {
            String websiteUrl = config.siteUrl();
            Desktop.getDesktop().browse(URI.create(websiteUrl));
            log.info("Opened website URL: {}", websiteUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to open website URL", e);
            updateStatus("Failed to open website", ERROR_COLOR);
        }
    }
    
    // Event item class for dropdown
    private static class EventItem
    {
        private final String bingoId;
        private final String name;
        private final String groupId;
        private final int durationDays;
        private final int daysRemaining;
        private final int totalTiles;
        private final boolean isActive;
        private final String prizePool;
        private final int participants;
        
        public EventItem(String bingoId, String name, String groupId, int durationDays, int daysRemaining, int totalTiles, boolean isActive, String prizePool, int participants)
        {
            this.bingoId = bingoId;
            this.name = name;
            this.groupId = groupId;
            this.durationDays = durationDays;
            this.daysRemaining = daysRemaining;
            this.totalTiles = totalTiles;
            this.isActive = isActive;
            this.prizePool = prizePool;
            this.participants = participants;
        }
        
        public String getBingoId() { return bingoId; }
        public String getName() { return name; }
        public String getGroupId() { return groupId; }
        public int getDurationDays() { return durationDays; }
        public int getDaysRemaining() { return daysRemaining; }
        public int getTotalTiles() { return totalTiles; }
        public boolean isActive() { return isActive; }
        public String getPrizePool() { return prizePool; }
        public int getParticipants() { return participants; }
        public boolean isEmpty() { return bingoId.isEmpty(); }
        
        @Override
        public String toString()
        {
            if (isEmpty())
            {
                return name;
            }
            return name + " (" + daysRemaining + " days left)";
        }
    }
    
    // Enhanced Custom renderer for event dropdown
    private static class EventItemRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof EventItem)
            {
                EventItem event = (EventItem) value;
                
                if (event.isEmpty())
                {
                    // Handle placeholder and error states
                    if (event.getName().equals("Select an event..."))
                    {
                        setText("▼ " + event.getName());
                        setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    }
                    else if (event.getName().contains("No") || event.getName().contains("Error"))
                    {
                        setText("⚠️ " + event.getName());
                        setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    }
                    else
                    {
                        setText(event.getName());
                        setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    }
                }
                else
                {
                    String prefix = event.isActive() ? "🟢 " : "🔴 ";
                    setText(prefix + event.toString());
                    
                    if (event.isActive())
                    {
                        setForeground(isSelected ? Color.WHITE : Color.WHITE);
                    }
                    else
                    {
                        setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                    }
                }
            }
            
            // Enhanced styling for better clarity
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            setOpaque(true);
            
            if (isSelected)
            {
                setBackground(ACCENT_COLOR);
                setForeground(Color.WHITE);
            }
            else
            {
                setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
            
            return this;
        }
    }
} 