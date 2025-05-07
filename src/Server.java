import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static List<PrintWriter> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("Serveur TCP démarré sur 8888");

        while (true) {
            Socket clientSocket = serverSocket.accept();

            // Nouvelle approche sans mark/reset
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
                out.println("HTTP/1.1 200 OK\r\n\r\nHealth Check OK");
                return;
            }

            // Mode messagerie
            clients.add(out);
            System.out.println("Nouveau client connecté");

            if (firstLine != null) {
                broadcast(firstLine);
            }

            String message;
            while ((message = in.readLine()) != null) {
                broadcast(message);
            }
        } catch (IOException e) {
            System.out.println("Erreur client: " + e.getMessage());
        } finally {
            clients.removeIf(writer -> {
                try {
                    return writer.checkError();
                } catch (Exception e) {
                    return true;
                }
            });
        }
    }

    private static void broadcast(String message) {
        System.out.println("Broadcast: " + message);
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