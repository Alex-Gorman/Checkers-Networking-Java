package checkers;

import javax.swing.*;
import java.net.UnknownHostException;

/**
 * Application entry point. Builds the main application frame and wires together
 * the three top-level panels: {@link MainMenu}, {@link HostMenu}, and {@link ClientMenu}.
 *
 * <p><strong>Note on Swing threading:</strong> For production code, consider creating
 * and showing the UI on the Event Dispatch Thread (EDT) via
 * {@code SwingUtilities.invokeLater(...)}. This sample keeps your original flow unchanged.</p>
 */
public class SwingFrame {

    /**
     * Launches the Checkers app and shows the main menu.
     *
     * @param args CLI args (unused)
     * @throws UnknownHostException kept from original signature; not required by current code
     */
    public static void main(String[] args) throws UnknownHostException {
        /* Top-level window for the application */
        JFrame frame = new JFrame("Checkers");

        /* Exit the process when the window is closed */
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        /* Initial window size (can be resized later by the user) */
        frame.setSize(1000, 1000);

        /* Build the two sub-menus that MainMenu routes to */
        ClientMenu clientMenu = new ClientMenu(frame);
        HostMenu hostMenu   = new HostMenu(frame);

        /* Main menu gets references to the host/client panels for navigation */
        MainMenu mainMenu = new MainMenu(frame, hostMenu, clientMenu);

        /* Allow the sub-menus to navigate back to the main menu */
        clientMenu.addMainPanel(mainMenu);
        hostMenu.addMainPanel(mainMenu);

        /* Start on the main menu */
        frame.getContentPane().add(mainMenu);

        /* Show the window */
        frame.setVisible(true);
    }
}
