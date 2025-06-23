package wzd.bingo.ui;

import wzd.bingo.BingoService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.ImageUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

public class AuthPanel extends PluginPanel
{
    private final BingoService bingoService;
    private final ConfigManager configManager;
    private final Client client;
    private JTextField rsnField;
    private JTextField tokenField;
    private JButton linkBtn;
    private Timer updateTimer;

    public AuthPanel(BingoService bingoService, ConfigManager configManager, Client client)
    {
        this.bingoService = bingoService;
        this.configManager = configManager;
        this.client = client;

        setLayout(new BorderLayout());
        setBackground(new Color(40, 40, 40)); // Dark theme background
        
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

        setupButtonAction();
    }

    private JPanel createHeaderPanel()
    {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(40, 40, 40));
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Try to load the header image
        BufferedImage headerImage = ImageUtil.loadImageResource(getClass(), "/wzd/bingo/clan-bingo-header.png");
        
        if (headerImage != null)
        {
            JLabel imageLabel = new JLabel(new ImageIcon(headerImage));
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            headerPanel.add(imageLabel, BorderLayout.CENTER);
        }
        else
        {
            // Fallback text header
            JLabel header = new JLabel("clan.bingo Authentication", SwingConstants.CENTER);
            header.setFont(new Font("Arial", Font.BOLD, 18));
            header.setForeground(Color.WHITE);
            headerPanel.add(header, BorderLayout.CENTER);
        }

        return headerPanel;
    }

    private JPanel createMainPanel()
    {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(new Color(50, 50, 50));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // RSN Section
        JPanel rsnPanel = createFieldPanel("RuneScape Name:", true);
        rsnField = new JTextField();
        rsnField.setEditable(false);
        rsnField.setBackground(new Color(70, 70, 70));
        rsnField.setForeground(Color.WHITE);
        rsnField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            new EmptyBorder(8, 12, 8, 12)
        ));
        rsnField.setFont(new Font("Arial", Font.PLAIN, 14));
        rsnField.setToolTipText("Your RSN will be detected automatically when logged in");
        rsnPanel.add(rsnField);

        // Token Section
        JPanel tokenPanel = createFieldPanel("Authentication Token:", false);
        tokenField = new JTextField();
        tokenField.setText(configManager.getConfiguration("bingo", "authToken", ""));
        tokenField.setBackground(new Color(60, 60, 60));
        tokenField.setForeground(Color.WHITE);
        tokenField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            new EmptyBorder(8, 12, 8, 12)
        ));
        tokenField.setFont(new Font("Arial", Font.PLAIN, 14));
        tokenField.setToolTipText("Enter the token generated on clan.bingo website");
        tokenPanel.add(tokenField);

        // Button Section
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttonPanel.setBackground(new Color(50, 50, 50));
        linkBtn = new JButton("Link Account");
        linkBtn.setBackground(new Color(70, 130, 180));
        linkBtn.setForeground(Color.WHITE);
        linkBtn.setFont(new Font("Arial", Font.BOLD, 14));
        linkBtn.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        linkBtn.setFocusPainted(false);
        linkBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Button hover effect
        linkBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                linkBtn.setBackground(new Color(100, 150, 200));
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                linkBtn.setBackground(new Color(70, 130, 180));
            }
        });
        
        buttonPanel.add(linkBtn);

        // Add components with spacing
        mainPanel.add(rsnPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(tokenPanel);
        mainPanel.add(Box.createVerticalStrut(20));
        mainPanel.add(buttonPanel);

        return mainPanel;
    }

    private JPanel createFieldPanel(String labelText, boolean isReadOnly)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(50, 50, 50));

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Arial", Font.BOLD, 13));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        if (isReadOnly)
        {
            JLabel subLabel = new JLabel("(Auto-detected when logged in)");
            subLabel.setForeground(new Color(180, 180, 180));
            subLabel.setFont(new Font("Arial", Font.ITALIC, 11));
            subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(label);
            panel.add(subLabel);
        }
        else
        {
            panel.add(label);
        }
        
        panel.add(Box.createVerticalStrut(5));
        return panel;
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

    private void setupButtonAction()
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
            rsnField.setBackground(new Color(60, 90, 60)); // Green tint for logged in
        }
        else
        {
            rsnField.setText("(Must be logged in first)");
            rsnField.setBackground(new Color(90, 60, 60)); // Red tint for not logged in
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