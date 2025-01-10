import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MIbayAgent {
    private static final int CLIPORT = 12345;
    private static final String BROADCAST_ADDRESS = "255.255.255.255";
    private static final int BROADCAST_PORT = 6000;
    private static int balance;
    static final Map<String, Auction> auctions = new ConcurrentHashMap<>();
    static final Map<String, Bid> bids = new ConcurrentHashMap<>();
    static String command;
    static String pathToFile = "/dateien/";

    static Thread CLIListener = new Thread(() -> {
        try (DatagramSocket CLISocket = new DatagramSocket(CLIPORT)) {
            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                CLISocket.receive(packet);

                command = new String(packet.getData(), 0, packet.getLength());
                String[] commandParts = command.split(" ");

                switch (commandParts[0]) {
                    case "anbieten":
                        anbieten(Integer.parseInt(commandParts[1]), Integer.parseInt(commandParts[2]), commandParts[3]);
                        break;
                    case "bieten":
                        bieten(Integer.parseInt(commandParts[1]), commandParts[2], commandParts[3]);
                        break;
                    case "abbrechen":
                        abbrechen(commandParts[1]);
                        break;
                    case "liste":
                        liste();
                        break;
                    case "info":
                        info();
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    });

    static Thread requestListener = new Thread(() -> {
        try (DatagramSocket requestSocket = new DatagramSocket(BROADCAST_PORT)) {
            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                requestSocket.receive(packet);

                byte[] data = packet.getData();
                String receivedPrefix = new String(data, 0, 5);

                if ("FILE:".equals(receivedPrefix)) {
                    byte[] fileData = new byte[packet.getLength() - 5];
                    System.arraycopy(data, 5, fileData, 0, fileData.length);
                    Files.write(new File("dateien/received.txt").toPath(), fileData);
                    System.out.println("File received and saved to dateien/received.txt");
                } else {
                    String request = new String(packet.getData(), 0, packet.getLength());
                    String[] requestParts = request.split(":");

                    switch (requestParts[0]) {
                        case "nachricht":
                            System.out.println(requestParts[1] + "!!!!");
                            break;

                        case "CHECK_NAME":
                            if (requestParts[1].equals(System.getenv("USER"))) {
                                String response = InetAddress.getLocalHost().getHostAddress();
                                DatagramPacket responsePacket = new DatagramPacket(response.getBytes(),
                                        response.length(),
                                        packet.getAddress(), packet.getPort());
                                requestSocket.send(responsePacket);
                            }
                            break;

                        case "auctions":
                            StringBuilder auctionsList = new StringBuilder();
                            for (Auction auction : auctions.values()) {
                                auctionsList
                                        .append("Höchstgebot: " + auction.highestBid + " | Bieter: "
                                                + auction.highestBidder + " | Status: "
                                                + (auction.ongoing ? "laufend" : "beendet")
                                                + " | Anbieter: " + auction.seller + " | Datei: " + auction.fileName
                                                + "\n");
                            }
                            String response = auctionsList.toString();
                            DatagramPacket responsePacket = new DatagramPacket(response.getBytes(), response.length(),
                                    packet.getAddress(), packet.getPort());
                            requestSocket.send(responsePacket);
                            break;

                        case "abbrechen":
                            if (auctions.containsKey(requestParts[1])) {
                                auctions.remove(requestParts[1]);
                            }
                            if (bids.containsKey(requestParts[1])) {
                                bids.remove(requestParts[1]);
                            }
                            break;

                        case "bieten":
                            String responseMessage = "nachricht:Gebot erfolgreich.";
                            String[] bidParts = requestParts[1].split(";");
                            if (auctions.containsKey(bidParts[2])) {
                                if (auctions.get(bidParts[2]).ongoing
                                        && (Integer.parseInt(bidParts[1]) > auctions.get(bidParts[2]).highestBid)) {
                                    auctions.get(bidParts[2]).highestBid = Integer.parseInt(bidParts[1]);
                                    auctions.get(bidParts[2]).highestBidder = bidParts[0];
                                } else {
                                    responseMessage = "nachricht:Das Gebot ist zu niedrig oder die Auktion ist beendet.";
                                }
                            } else {
                                responseMessage = "nachricht:Auktion nicht gefunden.";
                            }
                            InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);
                            DatagramPacket repPacket = new DatagramPacket(responseMessage.getBytes(),
                                    responseMessage.length(), broadcastAddress,
                                    BROADCAST_PORT);
                            requestSocket.send(repPacket);
                            break;

                        case "info":
                            break;
                    }
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    });

    public static void anbieten(int startPrice, int time, String filename) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(time * 1000);
            socket.setBroadcast(true);
            InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);

            String anbieten = "nachricht:" + System.getenv("USER") + " hat " + filename + " in " + time
                    + " Sekunden mit dem Startpreis " + startPrice + " angeboten.";

            DatagramPacket packet = new DatagramPacket(anbieten.getBytes(), anbieten.length(), broadcastAddress,
                    BROADCAST_PORT);
            socket.send(packet);

        } catch (SocketTimeoutException e) {
            System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Auction auction = new Auction(filename, startPrice, LocalTime.now().plusSeconds(time),
                System.getenv("USER"),
                pathToFile);
        auctions.put(filename, auction);

        new Thread(() -> {
            String message;
            while (!auction.canceled) {
                if (auction.expiryTime.isBefore(LocalTime.now())) {
                    auction.ongoing = false;
                    if (auction.highestBidder != null) {
                        message = "nachricht:Auktion für " + auction.fileName + " ist beendet. Gewinner ist "
                                + auction.highestBidder + " mit " + auction.highestBid;
                        sendFileToWinner(auction.fileName, auction.highestBidder);
                    } else {
                        message = "nachricht:Auktion für " + auction.fileName + " ist beendet. Kein Gewinner.";
                    }
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.setSoTimeout(time * 1000);
                        socket.setBroadcast(true);
                        InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);

                        DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                                broadcastAddress,
                                BROADCAST_PORT);
                        socket.send(packet);
                    } catch (SocketTimeoutException e) {
                        System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }).start();
    }

    public static void abbrechen(String filename) {
        if (auctions.containsKey(filename)) {
            auctions.get(filename).canceled = true;
            auctions.get(filename).ongoing = false;
            auctions.remove(filename);
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);
                InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);

                String bid = "nachricht:" + System.getenv("USER") + " hat " + filename + " abgebrochen. Kein Gewinner.";
                DatagramPacket packet = new DatagramPacket(bid.getBytes(), bid.length(), broadcastAddress,
                        BROADCAST_PORT);
                socket.send(packet);

                String cancelBid = "abbrechen:" + filename;
                DatagramPacket cancelPacket = new DatagramPacket(cancelBid.getBytes(), cancelBid.length(),
                        broadcastAddress,
                        BROADCAST_PORT);
                socket.send(cancelPacket);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Sie haben keine Auktion mit der Datei: " + filename);
        }
        if (bids.containsKey(filename)) {
            bids.remove(filename);
        }
    }

    public static void liste() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(10000);
            socket.setBroadcast(true);
            InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);

            String bid = "auctions:";
            DatagramPacket packet = new DatagramPacket(bid.getBytes(), bid.length(), broadcastAddress,
                    BROADCAST_PORT);
            socket.send(packet);

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);

            System.out.println("Auctions:\n");
            while (true) {
                try {
                    socket.receive(response);
                    String responseMessage = new String(response.getData(), 0, response.getLength());
                    System.out.println(responseMessage);
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void info() {

    }

    public static void bieten(int price, String username, String filename) {
        String userIP = findUser(username);
        if (userIP == null) {
            System.out.println("User not found");
            return;
        } else {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(10000);
                socket.setBroadcast(true);
                InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);

                String bid = "nachricht:" + System.getenv("USER") + " bietet " + price + " für " + filename + ".";

                DatagramPacket packet = new DatagramPacket(bid.getBytes(), bid.length(), broadcastAddress,
                        BROADCAST_PORT);
                socket.send(packet);
            } catch (SocketTimeoutException e) {
                System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
            } catch (IOException e) {
                e.printStackTrace();
            }

            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(10000);
                InetAddress userAddress = InetAddress.getByName(userIP);

                String bid = "bieten:" + System.getenv("USER") + ";" + price + ";" + filename;
                DatagramPacket packet = new DatagramPacket(bid.getBytes(), bid.length(), userAddress, BROADCAST_PORT);
                socket.send(packet);
            } catch (SocketTimeoutException e) {
                System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public static String findUser(String username) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(10000);

            String msg = "CHECK_NAME:" + username;
            InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);
            DatagramPacket request = new DatagramPacket(msg.getBytes(), msg.length(), broadcastAddress, BROADCAST_PORT);
            socket.send(request);

            byte[] buffer = new byte[512];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);

            while (true) {
                try {
                    socket.receive(response);
                    String responseAddress = new String(response.getData(), 0, response.getLength()).trim();
                    return responseAddress;
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void sendFileToWinner(String fileName, String winnerAddress) {
        try {
            String prefix = "FILE:" + fileName;

            File file = new File("../dateien/" + fileName);
            byte[] fileData = Files.readAllBytes(file.toPath());
            byte[] prefixBytes = prefix.getBytes();

            byte[] sendData = new byte[prefixBytes.length + fileData.length];
            System.arraycopy(prefixBytes, 0, sendData, 0, prefixBytes.length);
            System.arraycopy(fileData, 0, sendData, prefixBytes.length, fileData.length);

            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress receiverAddress = InetAddress.getByName(findUser(winnerAddress));
                DatagramPacket packet = new DatagramPacket(sendData, sendData.length, receiverAddress, BROADCAST_PORT);
                socket.send(packet);
                System.out.println("File sent with prefix: " + prefix);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Auction {
        String fileName;
        double minPrice;
        LocalTime expiryTime;
        String seller;
        String filePath;
        String highestBidder;
        double highestBid;
        boolean ongoing = false;
        boolean canceled = false;

        public Auction(String fileName, double minPrice, LocalTime expiryTime, String seller, String filePath) {
            this.fileName = fileName;
            this.minPrice = minPrice;
            this.expiryTime = expiryTime;
            this.seller = seller;
            this.filePath = pathToFile + fileName;
            this.highestBid = minPrice;
            this.highestBidder = null;
            this.ongoing = true;
        }
    }

    static class Bid {
        String seller;
        double bid;
        String filename;

        public Bid(String seller, double bid, String filename) {
            this.seller = seller;
            this.bid = bid;
            this.filename = filename;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Bitte geben Sie Ihr Startguthaben ein.");
            return;
        }

        System.out.println("Warte auf Nachrichten...");

        balance = Integer.parseInt(args[0]);
        CLIListener.setName("CLIListener");
        CLIListener.start();
        System.out.println("CLIListener hat gestartet!");
        requestListener.setName("RequestListener");
        requestListener.start();
        System.out.println("RequestListener hat gestartet!");
    }
}
