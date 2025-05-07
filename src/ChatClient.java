import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.io.*;

public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private JTextArea chatArea;

    public static void main(String[] args) {
        new ChatClient().start();
    }

    public void start() {
        // Interface graphique
        JFrame frame = new JFrame("Chat TCP");
        chatArea = new JTextArea(10, 40);
        chatArea.setEditable(false);
        JTextField inputField = new JTextField(40);
        JButton sendButton = new JButton("Envoyer");

        sendButton.addActionListener(e -> {
            String message = inputField.getText();
            if (out != null && !message.isEmpty()) {
                out.println(message);
                chatArea.append("Moi: " + message + "\n");
                inputField.setText("");
            }
        });

        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(inputField);
        bottomPanel.add(sendButton);
        frame.add(bottomPanel, BorderLayout.SOUTH);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Connexion au serveur
        try {
            socket = new Socket("messagerie-oqm6.onrender.com", 8888);
            out = new PrintWriter(socket.getOutputStream(), true);

            // Thread pour recevoir les messages
            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                    String message;
                    while ((message = in.readLine()) != null) {
                        String finalMessage = message;
                        SwingUtilities.invokeLater(() -> chatArea.append("Autre: " + finalMessage + "\n"));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> chatArea.append("Déconnecté du serveur\n"));
                }
            }).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Impossible de se connecter au serveur");
        }
    }
}