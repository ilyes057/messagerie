import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888, 0, InetAddress.getByName("0.0.0.0"));
        System.out.println("Serveur démarré sur le port 8888...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler client = new ClientHandler(clientSocket);
            clients.add(client);
            new Thread(client).start();
        }
    }

    // Diffuse un message à tous les clients
    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Message reçu: " + inputLine);
                    broadcast(inputLine); // Renvoie à tous les clients
                }
            } catch (IOException e) {
                System.err.println("Erreur client: " + e.getMessage());
            } finally {
                clients.remove(this);
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
    }
}