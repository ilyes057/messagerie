import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("Serveur démarré sur 8888");

        while (true) {
            Socket clientSocket = serverSocket.accept();

            // Vérifie si c'est une requête HTTP de Render
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            String firstLine = in.readLine();

            if (firstLine != null && firstLine.startsWith("HEAD / HTTP")) {
                // Répond aux health checks de Render
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("HTTP/1.1 200 OK\r\n\r\n");
                clientSocket.close();
                continue;
            }

            // Sinon, traitement normal pour les clients de messagerie
            ClientHandler client = new ClientHandler(clientSocket, firstLine);
            clients.add(client);
            new Thread(client).start();
        }
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private String initialMessage;

        public ClientHandler(Socket socket, String initialMessage) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.initialMessage = initialMessage;
        }

        public void run() {
            try {
                // Traite le premier message déjà lu
                if (initialMessage != null) {
                    System.out.println("Message reçu: " + initialMessage);
                    broadcast(initialMessage);
                }

                // Continue avec les messages suivants
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Message reçu: " + inputLine);
                    broadcast(inputLine);
                }
            } catch (IOException e) {
                System.err.println("Erreur client: " + e.getMessage());
            } finally {
                clients.remove(this);
                try { socket.close(); } catch (IOException e) {}
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}