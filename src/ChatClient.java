import javax.swing.*;
import java.awt.*;
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
        JFrame frame = new JFrame("Chat TCP");
        chatArea = new JTextArea(10, 40);
        chatArea.setEditable(false);
        JTextField inputField = new JTextField(30);
        JButton sendButton = new JButton("Envoyer");

        sendButton.addActionListener(e -> sendMessage(inputField));
        inputField.addActionListener(e -> sendMessage(inputField));

        JPanel panel = new JPanel();
        panel.add(inputField);
        panel.add(sendButton);

        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.add(panel, BorderLayout.SOUTH);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        connectToServer();
    }

    private void sendMessage(JTextField inputField) {
        String message = inputField.getText();
        if (out != null && !message.isEmpty()) {
            out.println(message);
            chatArea.append("Moi: " + message + "\n");
            inputField.setText("");
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket("messagerie-oqm6.onrender.com", 10000);
            out = new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String msg = line;
                        SwingUtilities.invokeLater(() -> chatArea.append("Autre: " + msg + "\n"));
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> chatArea.append("Déconnecté du serveur\n"));
                }
            }).start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Impossible de se connecter au serveur");
        }
    }
}
