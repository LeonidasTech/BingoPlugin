package wzd.bingo.ui;

import wzd.bingo.BingoService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class AuthPanel extends PluginPanel
{
    private final BingoService bingoService;
    private final ConfigManager configManager;
    private final Client client;
    private JTextField rsnField;
    private JTextField tokenField;
    private JButton linkBtn;

    public AuthPanel(BingoService bingoService, ConfigManager configManager, Client client)
    {
        this.bingoService = bingoService;
        this.configManager = configManager;
        this.client = client;

        setLayout(new BorderLayout());
        JLabel header = new JLabel("Bingo Plugin Authentication", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(5, 1, 0, 5));
        
        // RSN field (read-only, auto-populated)
        form.add(new JLabel("RuneScape Name:"));
        rsnField = new JTextField();
        rsnField.setEditable(false);
        rsnField.setBackground(Color.LIGHT_GRAY);
        rsnField.setToolTipText("Must be logged in first - RSN will be detected automatically");
        form.add(rsnField);

        // Authentication token field
        form.add(new JLabel("Authentication Token (from website):"));
        tokenField = new JTextField();
        tokenField.setText(configManager.getConfiguration("bingo", "authToken", ""));
        tokenField.setToolTipText("Enter the token generated on the Bingo website");
        form.add(tokenField);

        // Link account button
        linkBtn = new JButton("Link Account");
        form.add(linkBtn);

        add(form, BorderLayout.CENTER);

        // Update RSN field when panel is displayed
        updateRsnField();

        linkBtn.addActionListener(e -> {
            String token = tokenField.getText().trim();
            if (token.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "Please enter your authentication token from the website.");
                return;
            }

            String rsn = getCurrentRsn();
            if (rsn == null || rsn.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "You must be logged into RuneScape first.");
                return;
            }

            // Attempt to authenticate with the token
            Optional<String> result = bingoService.authenticateWithToken(rsn, token);
            if (result.isPresent())
            {
                // Save the validated token and RSN
                configManager.setConfiguration("bingo", "rsn", rsn);
                configManager.setConfiguration("bingo", "authToken", token);
                JOptionPane.showMessageDialog(this, "Account linked successfully!");
                bingoService.initialize(); // Initialize with validated credentials
            }
            else
            {
                JOptionPane.showMessageDialog(this, "Authentication failed. Please check your token or try again.");
            }
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
            rsnField.setBackground(Color.WHITE);
        }
        else
        {
            rsnField.setText("(Must be logged in first)");
            rsnField.setBackground(Color.LIGHT_GRAY);
        }
    }

    // Call this method when the game state changes
    public void onGameStateChanged()
    {
        updateRsnField();
    }
} 