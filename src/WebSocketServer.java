import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class WebSocketServer {
    private static final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private static final String WEBSOCKET_MAGIC_STRING = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8888;

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("WebSocket serveur démarré sur port " + port);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Nouvelle connexion reçue de: " + clientSocket.getInetAddress());

            new Thread(() -> handleClient(clientSocket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();

            // Lire l'en-tête de la requête HTTP
            String line = in.readLine();
            if (line == null || !line.contains("GET")) {
                System.out.println("Pas une requête WebSocket. Fermeture.");
                socket.close();
                return;
            }

            Map<String, String> headers = new HashMap<>();
            while (!(line = in.readLine()).isEmpty()) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    headers.put(parts[0].trim(), parts[1].trim());
                }
            }

            // Vérifier si c'est une demande de WebSocket
            if (!headers.containsKey("Sec-WebSocket-Key")) {
                // C'est une requête HTTP normale, envoyer une page HTML simple
                sendHttpResponse(out);
                socket.close();
                return;
            }

            // Effectuer le handshake WebSocket
            String key = headers.get("Sec-WebSocket-Key") + WEBSOCKET_MAGIC_STRING;
            String acceptKey = Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8")));

            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

            out.write(response.getBytes("UTF-8"));
            out.flush();

            // Ajouter le client à la liste
            clients.add(socket);
            System.out.println("Client WebSocket connecté. Total: " + clients.size());

            // Lire les messages WebSocket
            handleWebSocketMessages(socket, in, out);

        } catch (Exception e) {
            System.out.println("Erreur: " + e.getMessage());
        } finally {
            try {
                clients.remove(socket);
                socket.close();
            } catch (Exception e) {
                // Ignorer les erreurs de fermeture
            }
            System.out.println("Client déconnecté. Clients restants: " + clients.size());
        }
    }

    private static void handleWebSocketMessages(Socket socket, BufferedReader in, OutputStream out) throws Exception {
        InputStream inputStream = socket.getInputStream();
        byte[] buffer = new byte[8192];

        while (true) {
            // Lire l'en-tête du frame WebSocket
            int read = inputStream.read(buffer, 0, 2);
            if (read == -1) break;

            byte firstByte = buffer[0];
            byte secondByte = buffer[1];

            boolean fin = (firstByte & 0x80) != 0;
            int opcode = firstByte & 0x0F;
            boolean masked = (secondByte & 0x80) != 0;
            int payloadLength = secondByte & 0x7F;

            // Si c'est un opcode de fermeture (8), fermer la connexion
            if (opcode == 8) {
                break;
            }

            // Gestion de la longueur étendue
            int payloadStart = 2;
            if (payloadLength == 126) {
                read = inputStream.read(buffer, payloadStart, 2);
                payloadLength = ((buffer[payloadStart] & 0xFF) << 8) | (buffer[payloadStart + 1] & 0xFF);
                payloadStart += 2;
            } else if (payloadLength == 127) {
                // Pour simplifier, nous ignorons les longueurs de 8 octets (64 bits)
                throw new Exception("Payload trop grand");
            }

            // Lire les clés de masquage (4 octets)
            byte[] maskingKey = new byte[4];
            if (masked) {
                read = inputStream.read(maskingKey);
                payloadStart += 4;
            }

            // Lire la charge utile
            byte[] payload = new byte[payloadLength];
            int totalRead = 0;
            while (totalRead < payloadLength) {
                read = inputStream.read(payload, totalRead, payloadLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }

            // Démasquer la charge utile
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ maskingKey[i % 4]);
                }
            }

            // Convertir la charge utile en texte
            String message = new String(payload, "UTF-8");
            System.out.println("Message reçu: " + message);

            // Diffuser le message à tous les clients
            broadcast(message);
        }
    }

    private static void broadcast(String message) {
        byte[] frameData = createWebSocketFrame(message);

        System.out.println("Broadcast: " + message);

        Iterator<Socket> iterator = clients.iterator();
        while (iterator.hasNext()) {
            Socket client = iterator.next();
            try {
                client.getOutputStream().write(frameData);
                client.getOutputStream().flush();
            } catch (IOException e) {
                System.err.println("Erreur envoi: " + e.getMessage());
                iterator.remove();
                try {
                    client.close();
                } catch (IOException ex) {
                    // Ignorer
                }
            }
        }
    }

    private static byte[] createWebSocketFrame(String message) {
        byte[] payload = message.getBytes();
        int payloadLength = payload.length;

        ByteArrayOutputStream frame = new ByteArrayOutputStream();

        // Premier octet: FIN (1) + RSV1,2,3 (000) + opcode (0001 = texte)
        frame.write(0x81);

        // Second octet: MASK (0) + longueur
        if (payloadLength < 126) {
            frame.write(payloadLength);
        } else if (payloadLength < 65536) {
            frame.write(126);
            frame.write((payloadLength >> 8) & 0xFF);
            frame.write(payloadLength & 0xFF);
        } else {
            frame.write(127);
            // Pour simplifier, nous ignorons les 4 premiers octets (32 bits) de la longueur 64 bits
            frame.write(0);
            frame.write(0);
            frame.write(0);
            frame.write(0);
            frame.write((payloadLength >> 24) & 0xFF);
            frame.write((payloadLength >> 16) & 0xFF);
            frame.write((payloadLength >> 8) & 0xFF);
            frame.write(payloadLength & 0xFF);
        }

        // Écrire la charge utile (non masquée pour le serveur)
        try {
            frame.write(payload);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return frame.toByteArray();
    }

    private static void sendHttpResponse(OutputStream out) throws IOException {
        String html = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <title>WebSocket Chat</title>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <style>\n" +
                "        body { font-family: Arial, sans-serif; margin: 0; padding: 20px; }\n" +
                "        #messages { height: 300px; border: 1px solid #ccc; overflow-y: scroll; margin-bottom: 10px; padding: 10px; }\n" +
                "        #messageForm { display: flex; }\n" +
                "        #messageInput { flex-grow: 1; padding: 5px; }\n" +
                "        button { padding: 5px 15px; background: #4CAF50; color: white; border: none; cursor: pointer; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <h1>WebSocket Chat</h1>\n" +
                "    <div id=\"messages\"></div>\n" +
                "    <form id=\"messageForm\">\n" +
                "        <input type=\"text\" id=\"messageInput\" placeholder=\"Entrez votre message...\" autocomplete=\"off\" />\n" +
                "        <button type=\"submit\">Envoyer</button>\n" +
                "    </form>\n" +
                "\n" +
                "    <script>\n" +
                "        const messagesDiv = document.getElementById('messages');\n" +
                "        const messageForm = document.getElementById('messageForm');\n" +
                "        const messageInput = document.getElementById('messageInput');\n" +
                "        \n" +
                "        // Créer une connexion WebSocket\n" +
                "        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';\n" +
                "        const wsUrl = `${protocol}//${window.location.host}${window.location.pathname}`;\n" +
                "        const socket = new WebSocket(wsUrl);\n" +
                "        \n" +
                "        socket.onopen = function(e) {\n" +
                "            addMessage('Système', 'Connecté au serveur');\n" +
                "        };\n" +
                "        \n" +
                "        socket.onmessage = function(event) {\n" +
                "            addMessage('Reçu', event.data);\n" +
                "        };\n" +
                "        \n" +
                "        socket.onclose = function(event) {\n" +
                "            addMessage('Système', 'Déconnecté du serveur');\n" +
                "        };\n" +
                "        \n" +
                "        socket.onerror = function(error) {\n" +
                "            addMessage('Erreur', 'Erreur de connexion WebSocket');\n" +
                "        };\n" +
                "        \n" +
                "        messageForm.addEventListener('submit', function(e) {\n" +
                "            e.preventDefault();\n" +
                "            if (messageInput.value) {\n" +
                "                socket.send(messageInput.value);\n" +
                "                addMessage('Envoyé', messageInput.value);\n" +
                "                messageInput.value = '';\n" +
                "            }\n" +
                "        });\n" +
                "        \n" +
                "        function addMessage(sender, msg) {\n" +
                "            const messageElement = document.createElement('div');\n" +
                "            messageElement.innerHTML = `<strong>${sender}:</strong> ${msg}`;\n" +
                "            messagesDiv.appendChild(messageElement);\n" +
                "            messagesDiv.scrollTop = messagesDiv.scrollHeight;\n" +
                "        }\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";

        String response = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + html.getBytes("UTF-8").length + "\r\n" +
                "Connection: close\r\n\r\n" +
                html;

        out.write(response.getBytes("UTF-8"));
        out.flush();
    }
}