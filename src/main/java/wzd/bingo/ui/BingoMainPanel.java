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
    private static final Color HOVER_COLOR = ColorScheme.BRAND_ORANGE;
    private static final Color BUTTON_COLOR = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color SUCCESS_COLOR = new Color(0, 150, 0);
    private static final Color ERROR_COLOR = ColorScheme.PROGRESS_ERROR_COLOR;

    private final BingoConfig config;
    private final BingoService bingoService;
    private final ConfigManager configManager;
    private final Runnable onLogout;
    
    private JLabel userInfoLabel;
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
    
    public BingoMainPanel(BingoConfig config, BingoService bingoService, ConfigManager configManager, Runnable onLogout)
    {
        this.config = config;
        this.bingoService = bingoService;
        this.configManager = configManager;
        this.onLogout = onLogout;
        
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        
        // Start periodic event refresh (every 5 minutes)
        startEventRefreshTimer();
        
        // Load initial data
        refreshActiveEvents();
        
        log.info("BingoMainPanel initialized");
    }
    
    private void initializeComponents()
    {
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // User info label
        userInfoLabel = new JLabel("Welcome, " + config.rsn());
        userInfoLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        userInfoLabel.setFont(userInfoLabel.getFont().deriveFont(Font.BOLD, 14f));
        userInfoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Event dropdown
        eventDropdown = new JComboBox<>();
        eventDropdown.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        eventDropdown.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        eventDropdown.setRenderer(new EventItemRenderer());
        
        // Event info labels
        eventNameLabel = createInfoLabel("No event selected");
        totalParticipantsLabel = createInfoLabel("Participants: -");
        prizePoolLabel = createInfoLabel("Prize Pool: -");
        timeRemainingLabel = createInfoLabel("Time Remaining: -");
        totalTilesLabel = createInfoLabel("Total Tiles: -");
        
        // View Board button
        viewBoardButton = createStyledButton("View Board");
        viewBoardButton.setEnabled(false);
        
        // Status label
        statusLabel = new JLabel("Loading events...");
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }
    
    private JLabel createInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(label.getFont().deriveFont(12f));
        return label;
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
                if (button.isEnabled())
                {
                    button.setBackground(HOVER_COLOR);
                }
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
        
        // Top buttons panel
        JPanel topButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        topButtonsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topButtonsPanel.add(createStyledButton("View Profile"));
        topButtonsPanel.add(createStyledButton("Settings"));
        topButtonsPanel.add(createStyledButton("Logout"));
        
        // User info section
        userInfoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Event selection section
        JLabel eventLabel = new JLabel("Active Events:");
        eventLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        eventLabel.setFont(eventLabel.getFont().deriveFont(Font.BOLD, 13f));
        eventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        eventDropdown.setAlignmentX(Component.LEFT_ALIGNMENT);
        eventDropdown.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        
        // Event info panel
        eventInfoPanel = new JPanel();
        eventInfoPanel.setLayout(new BoxLayout(eventInfoPanel, BoxLayout.Y_AXIS));
        eventInfoPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        eventInfoPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        eventInfoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        eventInfoPanel.add(eventNameLabel);
        eventInfoPanel.add(Box.createVerticalStrut(5));
        eventInfoPanel.add(totalParticipantsLabel);
        eventInfoPanel.add(Box.createVerticalStrut(5));
        eventInfoPanel.add(prizePoolLabel);
        eventInfoPanel.add(Box.createVerticalStrut(5));
        eventInfoPanel.add(timeRemainingLabel);
        eventInfoPanel.add(Box.createVerticalStrut(5));
        eventInfoPanel.add(totalTilesLabel);
        
        // View Board button panel
        JPanel viewBoardPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        viewBoardPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        viewBoardPanel.add(viewBoardButton);
        
        // Status label
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Add all components with proper spacing
        mainPanel.add(Box.createVerticalStrut(10));
        mainPanel.add(topButtonsPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(userInfoLabel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(eventLabel);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(eventDropdown);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(eventInfoPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(viewBoardPanel);
        mainPanel.add(Box.createVerticalStrut(10));
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
            headerPanel.add(headerLabel, BorderLayout.CENTER);
        }
        catch (Exception e)
        {
            // Fallback text header
            JLabel headerLabel = new JLabel("clan.bingo");
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
        // Event dropdown selection
        eventDropdown.addActionListener(e -> {
            EventItem selectedEvent = (EventItem) eventDropdown.getSelectedItem();
            if (selectedEvent != null && !selectedEvent.isEmpty())
            {
                updateEventInfo(selectedEvent);
                viewBoardButton.setEnabled(true);
            }
            else
            {
                clearEventInfo();
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
        
        // Top buttons - get references after creation
        Component[] components = ((JPanel) ((JPanel) getComponent(1)).getComponent(1)).getComponents();
        if (components.length >= 3)
        {
            ((JButton) components[0]).addActionListener(e -> openProfilePage());
            ((JButton) components[1]).addActionListener(e -> openSettings());
            ((JButton) components[2]).addActionListener(e -> handleLogout());
        }
    }
    
    private void openProfilePage()
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
    
    private void openSettings()
    {
        // TODO: Implement settings panel
        updateStatus("Settings panel coming soon", ColorScheme.LIGHT_GRAY_COLOR);
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
        
        new Thread(() -> {
            Optional<JsonObject> eventsData = bingoService.fetchActiveEvents();
            
            SwingUtilities.invokeLater(() -> {
                if (eventsData.isPresent())
                {
                    updateEventDropdown(eventsData.get());
                    updateStatus("Events loaded", SUCCESS_COLOR);
                }
                else
                {
                    updateStatus("Failed to load events", ERROR_COLOR);
                    eventDropdown.removeAllItems();
                    eventDropdown.addItem(new EventItem("", "No events available", "", 0, 0, 0, false));
                }
            });
        }).start();
    }
    
    private void updateEventDropdown(JsonObject eventsData)
    {
        eventDropdown.removeAllItems();
        
        boolean hasActiveEvent = eventsData.get("hasActiveEvent").getAsBoolean();
        
        if (hasActiveEvent)
        {
            JsonArray activeEvents = eventsData.getAsJsonArray("activeEvents");
            
            for (JsonElement eventElement : activeEvents)
            {
                JsonObject event = eventElement.getAsJsonObject();
                
                String bingoId = event.get("bingoId").getAsString();
                String name = event.get("name").getAsString();
                String groupId = event.has("groupId") ? event.get("groupId").getAsString() : "";
                int durationDays = event.get("durationDays").getAsInt();
                int daysRemaining = event.get("daysRemaining").getAsInt();
                int totalTiles = event.has("totalTiles") ? event.get("totalTiles").getAsInt() : 25; // Default bingo size
                boolean isActive = event.get("isActive").getAsBoolean();
                
                EventItem eventItem = new EventItem(bingoId, name, groupId, durationDays, daysRemaining, totalTiles, isActive);
                eventDropdown.addItem(eventItem);
            }
            
            // Select first event by default
            if (eventDropdown.getItemCount() > 0)
            {
                eventDropdown.setSelectedIndex(0);
            }
        }
        else
        {
            eventDropdown.addItem(new EventItem("", "No active events", "", 0, 0, 0, false));
        }
    }
    
    private void updateEventInfo(EventItem event)
    {
        eventNameLabel.setText("Event: " + event.getName());
        totalParticipantsLabel.setText("Participants: Loading..."); // TODO: Get from API
        prizePoolLabel.setText("Prize Pool: TBD"); // TODO: Get from API
        timeRemainingLabel.setText("Days Remaining: " + event.getDaysRemaining());
        totalTilesLabel.setText("Total Tiles: " + event.getTotalTiles());
    }
    
    private void clearEventInfo()
    {
        eventNameLabel.setText("No event selected");
        totalParticipantsLabel.setText("Participants: -");
        prizePoolLabel.setText("Prize Pool: -");
        timeRemainingLabel.setText("Time Remaining: -");
        totalTilesLabel.setText("Total Tiles: -");
    }
    
    private void updateStatus(String message, Color color)
    {
        statusLabel.setText(message);
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
        
        public EventItem(String bingoId, String name, String groupId, int durationDays, int daysRemaining, int totalTiles, boolean isActive)
        {
            this.bingoId = bingoId;
            this.name = name;
            this.groupId = groupId;
            this.durationDays = durationDays;
            this.daysRemaining = daysRemaining;
            this.totalTiles = totalTiles;
            this.isActive = isActive;
        }
        
        public String getBingoId() { return bingoId; }
        public String getName() { return name; }
        public String getGroupId() { return groupId; }
        public int getDurationDays() { return durationDays; }
        public int getDaysRemaining() { return daysRemaining; }
        public int getTotalTiles() { return totalTiles; }
        public boolean isActive() { return isActive; }
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
    
    // Custom renderer for event dropdown
    private static class EventItemRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof EventItem)
            {
                EventItem event = (EventItem) value;
                setText(event.toString());
                
                if (!event.isEmpty() && event.isActive())
                {
                    setForeground(isSelected ? Color.WHITE : SUCCESS_COLOR);
                }
                else
                {
                    setForeground(isSelected ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR);
                }
            }
            
            return this;
        }
    }
} 