import java.net.*;
import java.io.*;
import java.util.*;

public class Server {
    private static final List<PrintWriter> clients = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        // Utiliser le port fourni comme paramètre ou la variable d'environnement PORT
        int port = 8888;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Ignorer et utiliser le port par défaut
            }
        } else {
            // Essayer de récupérer depuis la variable d'environnement PORT
            String envPort = System.getenv("PORT");
            if (envPort != null && !envPort.isEmpty()) {
                try {
                    port = Integer.parseInt(envPort);
                } catch (NumberFormatException e) {
                    // Ignorer et utiliser le port par défaut
                }
            }
        }

        System.out.println("=== Démarrage du serveur de messagerie sur le port " + port + " ===");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Nouvelle connexion reçue de: " + clientSocket.getInetAddress());

                    // Traitement du client dans un nouveau thread
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (Exception e) {
                    System.err.println("Erreur lors de l'acceptation de la connexion: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Erreur fatale du serveur: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        PrintWriter out = null;

        try {
            // Créer les flux d'entrée/sortie
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out = new PrintWriter(clientSocket.getOutputStream(), true);

            // Lire la première ligne pour déterminer le type de requête
            String firstLine = in.readLine();

            if (firstLine == null) {
                System.out.println("Connexion fermée immédiatement");
                return;
            }

            System.out.println("Première ligne reçue: " + firstLine);

            // Vérifier si c'est une requête HTTP
            if (firstLine.startsWith("GET ") || firstLine.startsWith("HEAD ")) {
                handleHttpRequest(firstLine, in, out);
                return;
            }

            // Mode messagerie standard
            synchronized (clients) {
                clients.add(out);
            }
            System.out.println("Nouveau client de messagerie connecté. Total clients: " + clients.size());

            // Envoyer un message de bienvenue au client
            out.println("Bienvenue sur le serveur de messagerie! Utilisateurs connectés: " + clients.size());

            // Traiter la première ligne si non vide
            if (!firstLine.trim().isEmpty()) {
                broadcast("Client: " + firstLine);
            }

            // Lire et diffuser les messages suivants
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if (!inputLine.trim().isEmpty()) {
                    System.out.println("Message reçu: " + inputLine);
                    broadcast("Client: " + inputLine);
                }
            }

        } catch (IOException e) {
            System.out.println("Erreur client: " + e.getMessage());
        } finally {
            // Nettoyer les ressources et retirer le client de la liste
            if (out != null) {
                synchronized (clients) {
                    clients.remove(out);
                }
            }

            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignorer les erreurs de fermeture
            }

            System.out.println("Client déconnecté. Total clients restants: " + clients.size());
        }
    }

    private static void handleHttpRequest(String requestLine, BufferedReader in, PrintWriter out) {
        System.out.println("Requête HTTP détectée: " + requestLine);

        // Lire tous les en-têtes HTTP (jusqu'à une ligne vide)
        try {
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Ignorer les en-têtes
            }

            // Envoyer une page HTML simple avec une interface de chat
            StringBuilder html = new StringBuilder();
            html.append("HTTP/1.1 200 OK\r\n");
            html.append("Content-Type: text/html; charset=UTF-8\r\n");
            html.append("\r\n");
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n");
            html.append("<head>\n");
            html.append("    <title>Serveur de Messagerie</title>\n");
            html.append("    <meta charset=\"UTF-8\">\n");
            html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
            html.append("    <style>\n");
            html.append("        body { font-family: Arial, sans-serif; margin: 20px; }\n");
            html.append("        h1 { color: #2c3e50; }\n");
            html.append("        .info { background: #f8f9fa; padding: 15px; border-radius: 5px; }\n");
            html.append("    </style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("    <h1>Serveur de Messagerie</h1>\n");
            html.append("    <div class=\"info\">\n");
            html.append("        <p>Ce serveur est en cours d'exécution et accepte des connexions.</p>\n");
            html.append("        <p>Clients actuellement connectés: " + clients.size() + "</p>\n");
            html.append("        <p>Pour vous connecter au serveur de messagerie, utilisez:</p>\n");
            html.append("        <code>telnet " + InetAddress.getLocalHost().getHostName() + " " +
                    (System.getenv("PORT") != null ? System.getenv("PORT") : "8888") + "</code>\n");
            html.append("    </div>\n");
            html.append("</body>\n");
            html.append("</html>");

            out.print(html.toString());
            out.flush();

        } catch (IOException e) {
            System.err.println("Erreur lors du traitement de la requête HTTP: " + e.getMessage());
        }
    }

    private static void broadcast(String message) {
        System.out.println("Diffusion du message: " + message);

        synchronized (clients) {
            // Créer une copie pour éviter les problèmes de concurrence
            List<PrintWriter> clientsCopy = new ArrayList<>(clients);

            for (PrintWriter client : clientsCopy) {
                try {
                    client.println(message);
                    // Vérifier si l'envoi a échoué
                    if (client.checkError()) {
                        clients.remove(client);
                        System.out.println("Client retiré en raison d'une erreur d'envoi");
                    }
                } catch (Exception e) {
                    System.err.println("Erreur lors de la diffusion: " + e.getMessage());
                    clients.remove(client);
                }
            }
        }
    }
}