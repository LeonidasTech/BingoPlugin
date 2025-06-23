package wzd.bingo.ui;

import wzd.bingo.BingoService;
import wzd.bingo.BingoConfig;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.net.URI;

public class AuthPanel extends PluginPanel
{
    private final BingoService bingoService;
    private final BingoConfig config;
    private final ConfigManager configManager;
    private final Client client;
    private JTextField rsnField;
    private JTextField tokenField;
    private JButton linkBtn;
    private JButton visitProfileBtn;
    private Timer updateTimer;

    public AuthPanel(BingoService bingoService, BingoConfig config, ConfigManager configManager, Client client)
    {
        this.bingoService = bingoService;
        this.config = config;
        this.configManager = configManager;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        
        // Create header panel with image
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Create main form panel
        JPanel mainPanel = createMainPanel();
        add(mainPanel, BorderLayout.CENTER);

        // Start periodic RSN updates
        startRsnUpdateTimer();
        
        // Initial RSN field update
        updateRsnField();

        setupButtonActions();
    }

    private JPanel createHeaderPanel()
    {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 112, 192)),
            new EmptyBorder(10, 10, 10, 10)
        ));

        // Try to load the header image
        BufferedImage headerImage = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/clan-bingo-header.png");
        
        if (headerImage != null)
        {
            // Scale image to fit better in the header
            Image scaledImage = headerImage.getScaledInstance(200, -1, Image.SCALE_SMOOTH);
            JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            headerPanel.add(imageLabel, BorderLayout.CENTER);
        }
        else
        {
            // Fallback text header
            JLabel header = new JLabel("clan.bingo Authentication", SwingConstants.CENTER);
            header.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            header.setForeground(Color.WHITE);
            headerPanel.add(header, BorderLayout.CENTER);
        }

        return headerPanel;
    }

    private JPanel createMainPanel()
    {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // RSN Section
        mainPanel.add(createFieldSection("RuneScape Name:", createRsnField(), 
            "(Auto-detected when logged in)"));
        mainPanel.add(Box.createVerticalStrut(12));

        // Token Section
        mainPanel.add(createFieldSection("Authentication Token:", createTokenField(), null));
        mainPanel.add(Box.createVerticalStrut(8));

        // Instructions Section
        mainPanel.add(createInstructionsPanel());
        mainPanel.add(Box.createVerticalStrut(15));

        // Buttons Section
        mainPanel.add(createButtonsPanel());

        return mainPanel;
    }

    private JPanel createFieldSection(String labelText, JTextField field, String subText)
    {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARK_GRAY_COLOR);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(label);

        if (subText != null)
        {
            JLabel subLabel = new JLabel(subText);
            subLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            subLabel.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 10));
            subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(subLabel);
        }

        section.add(Box.createVerticalStrut(4));
        section.add(field);

        return section;
    }

    private JTextField createRsnField()
    {
        rsnField = new JTextField();
        rsnField.setEditable(false);
        rsnField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        rsnField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        rsnField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));
        rsnField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        rsnField.setMaximumSize(new Dimension(Integer.MAX_VALUE, rsnField.getPreferredSize().height));
        rsnField.setToolTipText("Your RSN will be detected automatically when logged in");
        return rsnField;
    }

    private JTextField createTokenField()
    {
        tokenField = new JTextField();
        tokenField.setText(configManager.getConfiguration("bingo", "authToken", ""));
        tokenField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tokenField.setForeground(Color.WHITE);
        tokenField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(6, 8, 6, 8)
        ));
        tokenField.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        tokenField.setMaximumSize(new Dimension(Integer.MAX_VALUE, tokenField.getPreferredSize().height));
        tokenField.setToolTipText("Enter the token from clan.bingo website");
        return tokenField;
    }

    private JPanel createInstructionsPanel()
    {
        JPanel instructionsPanel = new JPanel();
        instructionsPanel.setLayout(new BoxLayout(instructionsPanel, BoxLayout.Y_AXIS));
        instructionsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        instructionsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(8, 10, 8, 10)
        ));
        instructionsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Title
        JLabel titleLabel = new JLabel("How to get your token:");
        titleLabel.setForeground(new Color(255, 144, 64));
        titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        instructionsPanel.add(titleLabel);

        instructionsPanel.add(Box.createVerticalStrut(4));

        // Instructions - more compact
        String[] steps = {
            "1. Log into clan.bingo",
            "2. Account → View Profile",
            "3. Authentication Token section",
            "4. Click \"Generate Token\" if needed"
        };

        for (String step : steps)
        {
            JLabel stepLabel = new JLabel(step);
            stepLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            stepLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            instructionsPanel.add(stepLabel);
        }

        return instructionsPanel;
    }

    private JPanel createButtonsPanel()
    {
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Link Account button - RuneLite style
        linkBtn = new JButton("Link Account");
        linkBtn.setBackground(new Color(0, 112, 192));
        linkBtn.setForeground(Color.WHITE);
        linkBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        linkBtn.setBorder(new EmptyBorder(6, 12, 6, 12));
        linkBtn.setFocusPainted(false);
        linkBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Visit Profile button - secondary style
        visitProfileBtn = new JButton("Visit Profile");
        visitProfileBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        visitProfileBtn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        visitProfileBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        visitProfileBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            new EmptyBorder(5, 12, 5, 12)
        ));
        visitProfileBtn.setFocusPainted(false);
        visitProfileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effects
        linkBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                linkBtn.setBackground(new Color(0, 112, 192).brighter());
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                linkBtn.setBackground(new Color(0, 112, 192));
            }
        });

        visitProfileBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                visitProfileBtn.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                visitProfileBtn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        buttonsPanel.add(linkBtn);
        buttonsPanel.add(visitProfileBtn);

        return buttonsPanel;
    }

    private void setupButtonActions()
    {
        linkBtn.addActionListener(e -> {
            String token = tokenField.getText().trim();
            if (token.isEmpty())
            {
                showErrorDialog("Please enter your authentication token from clan.bingo website.");
                return;
            }

            String rsn = getCurrentRsn();
            if (rsn == null || rsn.isEmpty())
            {
                showErrorDialog("You must be logged into RuneScape first.");
                return;
            }

            // Show loading state
            linkBtn.setText("Linking...");
            linkBtn.setEnabled(false);

            // Attempt to authenticate with the token
            SwingUtilities.invokeLater(() -> {
                try {
                    Optional<String> result = bingoService.authenticateWithToken(rsn, token);
                    if (result.isPresent())
                    {
                        // Save the validated token and RSN
                configManager.setConfiguration("bingo", "rsn", rsn);
                configManager.setConfiguration("bingo", "authToken", token);
                        showSuccessDialog("Account linked successfully!");
                        bingoService.initialize(); // Initialize with validated credentials
            }
            else
            {
                        showErrorDialog("Authentication failed. Please check your token or try again.");
                    }
                } finally {
                    linkBtn.setText("Link Account");
                    linkBtn.setEnabled(true);
                }
            });
        });

        visitProfileBtn.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(config.profileUrl()));
            } catch (Exception ex) {
                showErrorDialog("Could not open browser. Please visit " + config.profileUrl() + " manually.");
            }
        });
    }

    private void startRsnUpdateTimer()
    {
        // Update RSN field every 2 seconds to catch login state changes
        updateTimer = new Timer(true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> updateRsnField());
            }
        }, 0, 2000); // Update every 2 seconds
    }

    private String getCurrentRsn()
    {
        if (client.getGameState() == GameState.LOGGED_IN && client.getLocalPlayer() != null)
        {
            return client.getLocalPlayer().getName();
        }
        return null;
    }

    private void updateRsnField()
    {
        String rsn = getCurrentRsn();
        if (rsn != null && !rsn.isEmpty())
        {
            rsnField.setText(rsn);
            rsnField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            rsnField.setForeground(Color.WHITE);
        }
        else
        {
            rsnField.setText("(Must be logged in first)");
            rsnField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            rsnField.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        }
    }

    private void showErrorDialog(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Authentication Error", JOptionPane.ERROR_MESSAGE);
    }

    private void showSuccessDialog(String message)
    {
        JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // Call this method when the game state changes
    public void onGameStateChanged()
    {
        updateRsnField();
    }

    // Clean up timer when panel is destroyed
    @Override
    public void removeNotify()
    {
        super.removeNotify();
        if (updateTimer != null)
        {
            updateTimer.cancel();
        }
    }
} 