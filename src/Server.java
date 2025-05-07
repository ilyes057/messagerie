import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static List<PrintWriter> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // Utiliser le port fourni par Render ou 8888 par défaut
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Serveur TCP démarré sur port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Nouvelle connexion reçue de: " + clientSocket.getInetAddress());

            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // Vérifie si c'est une requête HTTP
            String firstLine = in.readLine();
            if (firstLine != null &&
                    (firstLine.startsWith("GET ") || firstLine.startsWith("HEAD "))) {
                System.out.println("Requête HTTP reçue: " + firstLine);
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: text/plain");
                out.println("");
                out.println("Health Check OK");
                return;
            }

            // Mode messagerie
            synchronized(clients) {
                clients.add(out);
            }
            System.out.println("Nouveau client de messagerie connecté");

            if (firstLine != null) {
                broadcast(firstLine);
            }

            String message;
            while ((message = in.readLine()) != null) {
                System.out.println("Message reçu: " + message);
                broadcast(message);
            }
        } catch (IOException e) {
            System.out.println("Erreur client: " + e.getMessage());
        } finally {
            synchronized(clients) {
                clients.removeIf(writer -> {
                    try {
                        return writer.checkError();
                    } catch (Exception e) {
                        return true;
                    }
                });
            }
            System.out.println("Client déconnecté. Clients restants: " + clients.size());
        }
    }

    private static void broadcast(String message) {
        System.out.println("Broadcast: " + message);
        synchronized(clients) {
            for (PrintWriter client : new ArrayList<>(clients)) {
                try {
                    client.println(message);
                    client.flush();
                } catch (Exception e) {
                    System.err.println("Erreur envoi: " + e.getMessage());
                    clients.remove(client);
                }
            }
        }
    }
}