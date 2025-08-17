package checkers;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

/**
 * Entry screen for the application.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Provide navigation to the Host and Join (Away) flows.</li>
 *   <li>Show the local machine's IP address for convenience when hosting.</li>
 * </ul>
 *
 * <p><strong>Navigation:</strong> Buttons swap panels directly on the provided
 * {@link JFrame}.
 */
public class MainMenu extends JPanel {

    /**
     * Builds the main menu with two actions (Host / Enter) and a local IP label.
     *
     * @param frame     the application frame; used for swapping panels
     * @param HostPanel the panel to show when "Host A Game" is clicked
     * @param AwayPanel the panel to show when "Enter A Game" is clicked
     */
    public MainMenu(JFrame frame, JPanel HostPanel, JPanel AwayPanel) {

        // Buttons for the two flows
        JButton hostGameButton = new JButton("Host A Game");
        JButton AwayGameButton = new JButton("Enter A Game");

        // Optional: label that displays the local IPv4 address
        JLabel myHostAddr;

        // Layout
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        // Button sizing/typography
        hostGameButton.setPreferredSize(new Dimension(350, 150));
        hostGameButton.setFont(new Font("", Font.PLAIN, 20));
        AwayGameButton.setPreferredSize(new Dimension(350, 150));
        AwayGameButton.setFont(new Font("", Font.PLAIN, 20));

        // Place "Host" button (row 0)
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        add(hostGameButton, gbc);

        // Place "Enter" button (row 1)
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(AwayGameButton, gbc);

        // Background theme
        setBackground(new Color(159, 235, 237));

        // Navigate to Host panel
        hostGameButton.addActionListener(e -> {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(HostPanel);
            frame.revalidate();
            frame.repaint();
        });

        // Navigate to Away/Join panel
        AwayGameButton.addActionListener(e -> {
            frame.getContentPane().removeAll();
            frame.getContentPane().add(AwayPanel);
            frame.revalidate();
            frame.repaint();
        });

        // Best-effort local IP label (may throw on some setups—kept silent like original code)
        try {
            String ip = String.valueOf(InetAddress.getLocalHost()).split("/")[1];
            myHostAddr = new JLabel("My IP Address: " + ip, SwingConstants.CENTER);

            // Make background visible and add simple styling
            myHostAddr.setOpaque(true);
            myHostAddr.setBackground(new Color(51, 255, 249));
            myHostAddr.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
            myHostAddr.setFont(new Font("", Font.BOLD, 20));

            // Place the label (row 2)
            gbc.gridx = 0;
            gbc.gridy = 2;
            gbc.gridwidth = 2;
            add(myHostAddr, gbc);
        } catch (Exception ignored) {
            // If we can't resolve the local host address, just omit the label.
        }
    }
}
