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

            // Crée un nouveau flux tamponné sans consommer les données
            PushbackInputStream pbStream = new PushbackInputStream(clientSocket.getInputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(pbStream));

            // Lit les premiers caractères sans les consommer
            in.mark(5);
            char[] start = new char[4];
            int read = in.read(start, 0, 4);
            in.reset();

            if (read == 4 && (new String(start).startsWith("GET ") || new String(start).startsWith("HEAD"))) {
                // Réponse HTTP pour Render
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nOK");
                clientSocket.close();
                continue;
            }

            // Si ce n'est pas HTTP, crée le handler avec le socket original
            ClientHandler client = new ClientHandler(new SocketWrapper(clientSocket, pbStream));
            clients.add(client);
            new Thread(client).start();
        }
    }

    static class SocketWrapper {
        private final Socket socket;
        private final PushbackInputStream stream;

        public SocketWrapper(Socket socket, PushbackInputStream stream) {
            this.socket = socket;
            this.stream = stream;
        }

        public InputStream getInputStream() {
            return stream;
        }

        public OutputStream getOutputStream() throws IOException {
            return socket.getOutputStream();
        }
    }

    static class ClientHandler implements Runnable {
        private final SocketWrapper socketWrapper;
        private final PrintWriter out;

        public ClientHandler(SocketWrapper socketWrapper) throws IOException {
            this.socketWrapper = socketWrapper;
            this.out = new PrintWriter(socketWrapper.getOutputStream(), true);
        }

        public void run() {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(socketWrapper.getInputStream()))) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    System.out.println("Message reçu: " + inputLine);
                    broadcast(inputLine);
                }
            } catch (IOException e) {
                System.err.println("Erreur client: " + e.getMessage());
            } finally {
                clients.remove(this);
                try {
                    socketWrapper.socket.close();
                } catch (IOException e) {
                    System.err.println("Erreur fermeture socket: " + e.getMessage());
                }
            }
        }

        public void sendMessage(String message) {
            out.println(message);
            out.flush();
        }
    }

    public static void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}