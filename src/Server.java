import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static List<PrintWriter> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(8888);
        System.out.println("Serveur TCP démarré sur 8888 (compatible Render)");

        while (true) {
            Socket clientSocket = serverSocket.accept();

            // Vérification ultra-rapide pour les health checks
            InputStream rawInput = clientSocket.getInputStream();
            if (rawInput.available() > 0) {
                rawInput.mark(10);
                byte[] start = new byte[4];
                rawInput.read(start);
                rawInput.reset();

                if (new String(start).startsWith("GET ") || new String(start).startsWith("HEAD")) {
                    // Réponse HTTP minimaliste
                    OutputStream out = clientSocket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
                    clientSocket.close();
                    continue;
                }
            }

            // Client normal - mode messagerie
            PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);
            clients.add(writer);

            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()))) {

                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        System.out.println("Message: " + inputLine);
                        broadcast(inputLine);
                    }
                } catch (IOException e) {
                    System.out.println("Client déconnecté");
                } finally {
                    clients.remove(writer);
                }
            }).start();
        }
    }

    private static void broadcast(String message) {
        for (PrintWriter client : clients) {
            try {
                client.println(message);
                client.flush();
            } catch (Exception e) {
                System.out.println("Erreur envoi: " + e.getMessage());
            }
        }
    }
}