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
import java.text.SimpleDateFormat;
import java.util.Date;

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
    private Timer activityRefreshTimer;
    
    // Activity log components
    private JScrollPane activityLogPanel; // Now directly using scroll pane
    private JScrollPane activityScrollPane;
    private JList<ActivityLogEntry> activityList;
    private DefaultListModel<ActivityLogEntry> activityListModel;
    private boolean isParticipatingInEvent = false;
    
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
        
        // Start activity log refresh (every 30 seconds)
        startActivityRefreshTimer();
        
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
        
        // Initialize activity log components
        initializeActivityLog();
    }
    
    private JLabel createStyledInfoLabel(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(13f));
        return label;
    }
    
    private void initializeActivityLog()
    {
        // Create activity log list model and list
        activityListModel = new DefaultListModel<>();
        activityList = new JList<>(activityListModel);
        activityList.setBackground(new Color(35, 35, 35));
        activityList.setForeground(Color.WHITE);
        activityList.setFont(new Font("Monospaced", Font.PLAIN, 9));
        activityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activityList.setCellRenderer(new ActivityLogCellRenderer());
        
        // Add mouse listener for screenshot links
        activityList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = activityList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    ActivityLogEntry entry = activityListModel.getElementAt(index);
                    if (entry.hasScreenshot()) {
                        openScreenshot(entry.getScreenshotUrl());
                    }
                }
            }
        });
        
        // Create scroll pane for activity log with direct styling
        activityScrollPane = new JScrollPane(activityList);
        activityScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        activityScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        activityScrollPane.setPreferredSize(new Dimension(0, 180));
        activityScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(CARD_BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(3, 3, 3, 3)
        ));
        activityScrollPane.setBackground(INFO_PANEL_COLOR);
        
        // Use scroll pane directly as activity log panel (remove wrapper)
        activityLogPanel = activityScrollPane;
        
        // Initially hide the activity log
        activityLogPanel.setVisible(false);
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
    
    private void openScreenshot(String screenshotUrl)
    {
        // Check if user has disabled screenshot confirmation
        Boolean neverAskAgainObj = configManager.getConfiguration("bingo", "neverAskScreenshot", Boolean.class);
        boolean neverAskAgain = neverAskAgainObj != null && neverAskAgainObj;
        
        if (neverAskAgain)
        {
            // Open directly without asking
            openScreenshotDirect(screenshotUrl);
        }
        else
        {
            // Show confirmation dialog
            showScreenshotConfirmationDialog(screenshotUrl);
        }
    }
    
    private void openScreenshotDirect(String screenshotUrl)
    {
        try
        {
            Desktop.getDesktop().browse(URI.create(screenshotUrl));
            log.info("Opened screenshot URL: {}", screenshotUrl);
        }
        catch (IOException e)
        {
            log.error("Failed to open screenshot URL: {}", screenshotUrl, e);
            updateStatus("Failed to open screenshot", ERROR_COLOR);
        }
    }
    
    private void showScreenshotConfirmationDialog(String screenshotUrl)
    {
        JDialog dialog = new JDialog();
        dialog.setTitle("Open Screenshot");
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(400, 150);
        dialog.setLocationRelativeTo(this);
        
        // Set dark theme for dialog
        dialog.getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Message label
        JLabel messageLabel = new JLabel("Are you sure you wish to open the screenshot?");
        messageLabel.setForeground(Color.WHITE);
        messageLabel.setFont(messageLabel.getFont().deriveFont(13f));
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // Never ask again checkbox
        JCheckBox neverAskCheckbox = new JCheckBox("Never ask again");
        neverAskCheckbox.setForeground(Color.WHITE);
        neverAskCheckbox.setBackground(ColorScheme.DARK_GRAY_COLOR);
        neverAskCheckbox.setFont(neverAskCheckbox.getFont().deriveFont(12f));
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        JButton yesButton = new JButton("Yes");
        yesButton.setBackground(SUCCESS_COLOR);
        yesButton.setForeground(Color.WHITE);
        yesButton.setFocusPainted(false);
        yesButton.setBorderPainted(false);
        yesButton.setPreferredSize(new Dimension(80, 30));
        
        JButton noButton = new JButton("No");
        noButton.setBackground(ERROR_COLOR);
        noButton.setForeground(Color.WHITE);
        noButton.setFocusPainted(false);
        noButton.setBorderPainted(false);
        noButton.setPreferredSize(new Dimension(80, 30));
        
        // Button actions
        yesButton.addActionListener(e -> {
            if (neverAskCheckbox.isSelected())
            {
                configManager.setConfiguration("bingo", "neverAskScreenshot", true);
                log.info("User enabled 'never ask again' for screenshots");
            }
            dialog.dispose();
            openScreenshotDirect(screenshotUrl);
        });
        
        noButton.addActionListener(e -> {
            if (neverAskCheckbox.isSelected())
            {
                configManager.setConfiguration("bingo", "neverAskScreenshot", true);
                log.info("User enabled 'never ask again' for screenshots");
            }
            dialog.dispose();
        });
        
        buttonPanel.add(yesButton);
        buttonPanel.add(noButton);
        
        // Checkbox panel
        JPanel checkboxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        checkboxPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        checkboxPanel.add(neverAskCheckbox);
        
        mainPanel.add(messageLabel, BorderLayout.NORTH);
        mainPanel.add(checkboxPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel);
        dialog.setVisible(true);
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
        
        // Activity log panel (initially hidden)
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.weighty = 0.6; // Give activity log some vertical space
        gbc.fill = GridBagConstraints.BOTH;
        mainPanel.add(activityLogPanel, gbc);
        
        // Action buttons panel (moved below activity log)
        JPanel actionPanel = createActionPanel();
        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 8, 0);
        gbc.weighty = 0.0; // Reset weight for button panel
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainPanel.add(actionPanel, gbc);
        
        // Status panel
        JPanel statusPanelBottom = createStatusPanel();
        gbc.gridy = 5;
        gbc.weighty = 0.1; // Reduce status panel weight to give room for activity log
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
            if (selectedEvent != null && !selectedEvent.isEmpty() && !selectedEvent.getName().equals("Select an event..."))
            {
                updateEventInfo(selectedEvent);
                showEventDetails(true);
                viewBoardButton.setEnabled(true);
                
                // Show activity log and start participating
                isParticipatingInEvent = true;
                activityLogPanel.setVisible(true);
                refreshActivityLog(selectedEvent.getBingoId());
            }
            else
            {
                clearEventInfo();
                showEventDetails(false);
                viewBoardButton.setEnabled(false);
                
                // Hide activity log and stop participating
                isParticipatingInEvent = false;
                activityLogPanel.setVisible(false);
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
    
    private void startActivityRefreshTimer()
    {
        // Refresh activity log every 30 seconds
        activityRefreshTimer = new Timer(30 * 1000, e -> {
            if (isParticipatingInEvent)
            {
                EventItem selectedEvent = (EventItem) eventDropdown.getSelectedItem();
                if (selectedEvent != null && !selectedEvent.isEmpty())
                {
                    refreshActivityLog(selectedEvent.getBingoId());
                }
            }
        });
        activityRefreshTimer.start();
    }
    
    private void refreshActivityLog(String bingoId)
    {
        // Fetch activity log from backend in a background thread
        new Thread(() -> {
            try
            {
                Optional<JsonObject> activityData = bingoService.fetchActivityLog(bingoId);
                if (activityData.isPresent())
                {
                    SwingUtilities.invokeLater(() -> updateActivityLog(activityData.get()));
                }
            }
            catch (Exception e)
            {
                log.error("Failed to fetch activity log", e);
                SwingUtilities.invokeLater(() -> {
                    activityListModel.clear();
                    ActivityLogEntry errorEntry = new ActivityLogEntry("", "ERROR", "", "", 0, "", "", 
                        "Failed to load activity log. Check connection.", 0);
                    activityListModel.addElement(errorEntry);
                });
            }
        }).start();
    }
    
    private void updateActivityLog(JsonObject activityData)
    {
        try
        {
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm-dd/MM");
            activityListModel.clear();
            
            if (activityData.has("activities"))
            {
                JsonArray activities = activityData.getAsJsonArray("activities");
                for (JsonElement activityElement : activities)
                {
                    JsonObject activity = activityElement.getAsJsonObject();
                    
                    String playerRsn = activity.get("playerRsn").getAsString();
                    String activityType = activity.get("activityType").getAsString();
                    String timestamp = activity.get("timestamp").getAsString();
                    String monsterName = activity.has("monsterName") ? activity.get("monsterName").getAsString() : "";
                    String dropName = activity.has("dropName") && !activity.get("dropName").isJsonNull() ? activity.get("dropName").getAsString() : "";
                    int totalKc = activity.has("totalKc") ? activity.get("totalKc").getAsInt() : 0;
                    String screenshotUrl = activity.has("screenshotUrl") && !activity.get("screenshotUrl").isJsonNull() ? activity.get("screenshotUrl").getAsString() : "";
                    int teamId = activity.has("teamId") ? activity.get("teamId").getAsInt() : 0;
                    
                    // Parse timestamp and format
                    Date date = new Date(Long.parseLong(timestamp) * 1000);
                    String formattedTime = formatter.format(date);
                    
                    ActivityLogEntry entry = new ActivityLogEntry(playerRsn, activityType, monsterName, 
                        dropName, totalKc, screenshotUrl, timestamp, formattedTime, teamId);
                    
                    activityListModel.addElement(entry);
                }
            }
            
            if (activityListModel.isEmpty())
            {
                // Add a placeholder entry
                ActivityLogEntry placeholder = new ActivityLogEntry("", "INFO", "", "", 0, "", "", 
                    "No recent activity. Start participating to see your team's progress!", 0);
                activityListModel.addElement(placeholder);
            }
            
            // Scroll to bottom to show latest activity
            SwingUtilities.invokeLater(() -> {
                if (activityListModel.getSize() > 0) {
                    activityList.ensureIndexIsVisible(activityListModel.getSize() - 1);
                }
            });
        }
        catch (Exception e)
        {
            log.error("Failed to update activity log", e);
            activityListModel.clear();
            ActivityLogEntry errorEntry = new ActivityLogEntry("", "ERROR", "", "", 0, "", "", 
                "Error displaying activity log.", 0);
            activityListModel.addElement(errorEntry);
        }
    }
    
    public void shutdown()
    {
        if (eventRefreshTimer != null)
        {
            eventRefreshTimer.stop();
        }
        if (activityRefreshTimer != null)
        {
            activityRefreshTimer.stop();
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
    
    // Custom renderer for activity log list
    private static class ActivityLogCellRenderer extends DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus)
        {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ActivityLogEntry)
            {
                ActivityLogEntry entry = (ActivityLogEntry) value;
                
                // Set the display text (no [View Screenshot] text needed)
                setText(entry.getDisplayText());
                
                // Style based on activity type and screenshot availability
                if (entry.getActivityType().equals("DROP") && entry.hasScreenshot())
                {
                    setForeground(isSelected ? Color.WHITE : new Color(255, 215, 0)); // Gold for drops with screenshots
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                else
                {
                    setForeground(isSelected ? Color.WHITE : Color.WHITE);
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                
                // Set background colors
                if (isSelected)
                {
                    setBackground(ACCENT_COLOR);
                }
                else
                {
                    setBackground(new Color(35, 35, 35));
                }
                
                setFont(new Font("Monospaced", Font.PLAIN, 9));
                setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
            }
            
            return this;
        }
    }

    // Activity log entry class
    private static class ActivityLogEntry
    {
        private final String playerRsn;
        private final String activityType;
        private final String monsterName;
        private final String dropName;
        private final int totalKc;
        private final String screenshotUrl;
        private final String timestamp;
        private final String formattedTime;
        private final int teamId;
        
        public ActivityLogEntry(String playerRsn, String activityType, String monsterName, 
                              String dropName, int totalKc, String screenshotUrl, String timestamp, 
                              String formattedTime, int teamId)
        {
            this.playerRsn = playerRsn;
            this.activityType = activityType;
            this.monsterName = monsterName;
            this.dropName = dropName;
            this.totalKc = totalKc;
            this.screenshotUrl = screenshotUrl;
            this.timestamp = timestamp;
            this.formattedTime = formattedTime;
            this.teamId = teamId;
        }
        
        public String getPlayerRsn() { return playerRsn; }
        public String getActivityType() { return activityType; }
        public String getMonsterName() { return monsterName; }
        public String getDropName() { return dropName; }
        public int getTotalKc() { return totalKc; }
        public String getScreenshotUrl() { return screenshotUrl; }
        public String getTimestamp() { return timestamp; }
        public String getFormattedTime() { return formattedTime; }
        public int getTeamId() { return teamId; }
        public boolean hasScreenshot() { return screenshotUrl != null && !screenshotUrl.isEmpty(); }
        
        private String abbreviateName(String name)
        {
            if (name == null || name.isEmpty()) return name;
            
            // Common raid abbreviations
            if (name.equalsIgnoreCase("Theatre of Blood")) return "T.o.B";
            if (name.equalsIgnoreCase("Chambers of Xeric")) return "C.o.X";
            if (name.equalsIgnoreCase("Tombs of Amascut")) return "T.o.A";
            
            // Boss abbreviations
            if (name.equalsIgnoreCase("King Black Dragon")) return "KBD";
            if (name.equalsIgnoreCase("Corporeal Beast")) return "Corp";
            if (name.equalsIgnoreCase("Commander Zilyana")) return "Zilyana";
            if (name.equalsIgnoreCase("General Graardor")) return "Graardor";
            if (name.equalsIgnoreCase("Kree'arra")) return "Kree'arra";
            if (name.equalsIgnoreCase("K'ril Tsutsaroth")) return "K'ril";
            if (name.equalsIgnoreCase("Dagannoth Prime")) return "DK Prime";
            if (name.equalsIgnoreCase("Dagannoth Rex")) return "DK Rex";
            if (name.equalsIgnoreCase("Dagannoth Supreme")) return "DK Supreme";
            if (name.equalsIgnoreCase("Barrows Brothers")) return "Barrows";
            if (name.equalsIgnoreCase("Giant Mole")) return "Mole";
            if (name.equalsIgnoreCase("Kalphite Queen")) return "KQ";
            if (name.equalsIgnoreCase("Chaos Elemental")) return "Chaos Ele";
            if (name.equalsIgnoreCase("Crazy Archaeologist")) return "C.Arch";
            if (name.equalsIgnoreCase("Chaos Fanatic")) return "C.Fanatic";
            if (name.equalsIgnoreCase("Scorpia")) return "Scorpia";
            if (name.equalsIgnoreCase("Venenatis")) return "Venenatis";
            if (name.equalsIgnoreCase("Vet'ion")) return "Vet'ion";
            if (name.equalsIgnoreCase("Callisto")) return "Callisto";
            if (name.equalsIgnoreCase("Zulrah")) return "Zulrah";
            if (name.equalsIgnoreCase("Vorkath")) return "Vorkath";
            if (name.equalsIgnoreCase("Alchemical Hydra")) return "Hydra";
            if (name.equalsIgnoreCase("The Gauntlet")) return "Gauntlet";
            if (name.equalsIgnoreCase("The Corrupted Gauntlet")) return "C.Gauntlet";
            if (name.equalsIgnoreCase("The Nightmare")) return "Nightmare";
            if (name.equalsIgnoreCase("Phosani's Nightmare")) return "P.Nightmare";
            if (name.equalsIgnoreCase("Tempoross")) return "Tempoross";
            if (name.equalsIgnoreCase("Wintertodt")) return "Wintertodt";
            if (name.equalsIgnoreCase("Thermonuclear Smoke Devil")) return "Thermy";
            if (name.equalsIgnoreCase("Cerberus")) return "Cerberus";
            if (name.equalsIgnoreCase("Abyssal Sire")) return "Sire";
            if (name.equalsIgnoreCase("Kraken")) return "Kraken";
            if (name.equalsIgnoreCase("Grotesque Guardians")) return "Guardians";
            
            // Slayer monsters
            if (name.equalsIgnoreCase("Smoke Devil")) return "Smoke Devil";
            if (name.equalsIgnoreCase("Cave Horror")) return "Cave Horror";
            if (name.equalsIgnoreCase("Skeletal Wyvern")) return "Wyvern";
            
            return name; // Return original if no abbreviation found
        }
        
        public String getDisplayText()
        {
            switch (activityType)
            {
                case "BOSS_KILL":
                case "KILL":
                    return String.format("[%s] %s - %s (%dkc)", 
                        formattedTime, playerRsn, abbreviateName(monsterName), totalKc);
                    
                case "RAID_COMPLETION":
                    return String.format("[%s] %s - %s Raid (%dkc)", 
                        formattedTime, playerRsn, abbreviateName(monsterName), totalKc);
                    
                case "DROP":
                    return String.format("[%s] %s - %s (%dkc)", 
                        formattedTime, playerRsn, dropName, totalKc);
                        
                case "INFO":
                case "ERROR":
                    return formattedTime; // For placeholder messages, formattedTime contains the message
                    
                default:
                    return String.format("[%s] %s - %s", formattedTime, playerRsn, activityType);
            }
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