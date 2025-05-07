import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static final List<PrintWriter> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) {
        int port = 8888;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        } else {
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isEmpty()) {
                try {
                    port = Integer.parseInt(envPort);
                } catch (NumberFormatException ignored) {}
            }
        }

        System.out.println("=== Démarrage du serveur sur le port " + port + " ===");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion de: " + clientSocket.getInetAddress());
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String firstLine = in.readLine();
            if (firstLine == null) return;

            System.out.println("Première ligne: " + firstLine);

            if (firstLine.startsWith("GET ") || firstLine.startsWith("HEAD ")) {
                handleHttpRequest(firstLine, in, out);
                return;
            }

            clients.add(out);
            System.out.println("Client connecté. Total: " + clients.size());
            out.println("Bienvenue ! Clients connectés: " + clients.size());

            broadcast("Client: " + firstLine);
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    System.out.println("Message reçu: " + line);
                    broadcast("Client: " + line);
                }
            }
        } catch (IOException e) {
            System.out.println("Erreur client: " + e.getMessage());
        } finally {
            clients.removeIf(c -> c.checkError());
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            System.out.println("Client déconnecté. Restants: " + clients.size());
        }
    }

    private static void handleHttpRequest(String requestLine, BufferedReader in, PrintWriter out) throws IOException {
        while (in.readLine() != null && !in.readLine().isEmpty()) {}

        String html = "<!DOCTYPE html>\n" +
                "<html><head><title>Serveur</title><meta charset=\"UTF-8\"></head>\n" +
                "<body><h1>Serveur en ligne</h1>\n" +
                "<p>Clients connectés: " + clients.size() + "</p>\n" +
                "<p>Telnet: <code>telnet " + InetAddress.getLocalHost().getHostName() + " " +
                (System.getenv("PORT") != null ? System.getenv("PORT") : "8888") + "</code></p>\n" +
                "</body></html>";

        out.print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html);
        out.flush();
    }

    private static void broadcast(String message) {
        synchronized (clients) {
            for (Iterator<PrintWriter> it = clients.iterator(); it.hasNext();) {
                PrintWriter client = it.next();
                client.println(message);
                if (client.checkError()) {
                    it.remove();
                }
            }
        }
    }
}
