package com.example.bingo.ui;

import com.example.bingo.BingoService;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class AuthPanel extends PluginPanel
{
    private final BingoService bingoService;
    private final ConfigManager configManager;

    public AuthPanel(BingoService bingoService, ConfigManager configManager)
    {
        this.bingoService = bingoService;
        this.configManager = configManager;

        setLayout(new BorderLayout());
        JLabel header = new JLabel("Bingo Plugin Login", SwingConstants.CENTER);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 16f));
        add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 1, 0, 5));
        JTextField rsnField = new JTextField();
        rsnField.setText(configManager.getConfiguration("bingo", "rsn"));
        form.add(new JLabel("RuneScape Name (RSN):"));
        form.add(rsnField);

        JButton loginBtn = new JButton("Login & Link Account");
        form.add(loginBtn);

        add(form, BorderLayout.CENTER);

        loginBtn.addActionListener(e -> {
            String rsn = rsnField.getText().trim();
            if (rsn.isEmpty())
            {
                JOptionPane.showMessageDialog(this, "Please enter your RSN.");
                return;
            }

            Optional<String> tokenOpt = bingoService.login(rsn);
            if (tokenOpt.isPresent())
            {
                String token = tokenOpt.get();
                // Persist to RuneLite config
                configManager.setConfiguration("bingo", "rsn", rsn);
                configManager.setConfiguration("bingo", "authToken", token);
                JOptionPane.showMessageDialog(this, "Login successful!");
                bingoService.initialize(); // now that we have token, fetch board & team
            }
            else
            {
                JOptionPane.showMessageDialog(this, "Login failed. Check RSN or network.");
            }
        });
    }
} 