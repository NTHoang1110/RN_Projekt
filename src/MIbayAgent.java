import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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
    static String pathToFile = "dateien/";

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
            String fileNameWon = null;
            int priceWon = 0;
            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                requestSocket.receive(packet);

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
                            if (auction.ongoing && !auction.canceled) {
                                auctionsList
                                        .append("Höchstgebot: " + auction.highestBid + " | Bieter: "
                                                + auction.highestBidder + " | Status: "
                                                + (auction.ongoing ? "laufend" : "beendet")
                                                + " | Anbieter: " + auction.seller + " | Datei: " + auction.fileName
                                                + "\n");
                            }
                        }
                        String response = auctionsList.toString();
                        if (response.length() != 0) {
                            DatagramPacket responsePacket = new DatagramPacket(response.getBytes(),
                                    response.length(),
                                    packet.getAddress(), packet.getPort());
                            requestSocket.send(responsePacket);
                        }
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

                    case "gewonnen":
                        fileNameWon = requestParts[1];
                        if (bids.containsKey(fileNameWon)) {
                            bids.get(fileNameWon).won = true;
                            priceWon = bids.get(fileNameWon).bid;
                        }
                        break;

                    case "Geld":
                        balance += Integer.parseInt(requestParts[1]);
                        System.out.println("Geld erhalten: " + requestParts[1]);
                        break;

                    case "ended":
                        if (bids.containsKey(requestParts[1]) && !bids.get(requestParts[1]).won) {
                            bids.remove(requestParts[1]);
                        }
                        break;
                    case "File":
                        try (BufferedWriter bw = new BufferedWriter(
                                new FileWriter("dateien/" + fileNameWon, true))) {
                            if (requestParts[1].equals("EOF")) {
                                System.out.println("Datei empfangen und gespeichert in dateien/" + fileNameWon);
                                bw.close();
                                sendMoney(requestSocket, fileNameWon, priceWon);
                                bids.remove(fileNameWon);
                                break;
                            } else {
                                bw.write(requestParts[1]);
                                bw.newLine();
                            }
                        }
                        break;

                }

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    });

    public static void anbieten(int startPrice, int time, String filename) {
        File file = new File(/filename);
        if(file.exists())
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
            String winner = null;
            while (!auction.canceled) {
                if (auction.expiryTime.isBefore(LocalTime.now())) {
                    auction.ongoing = false;
                    if (auction.highestBidder != null) {
                        message = "nachricht:Auktion für " + auction.fileName + " ist beendet. Gewinner ist "
                                + auction.highestBidder + " mit " + auction.highestBid + " ";
                        winner = auction.highestBidder;
                        // sendFileToWinner(auction.fileName, auction.highestBidder);
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
                    try (DatagramSocket socket = new DatagramSocket()) {
                        socket.setSoTimeout(10000);
                        InetAddress userAddress = InetAddress.getByName(findUser(winner));
                        String bid = "gewonnen:" + auction.fileName;
                        DatagramPacket packet = new DatagramPacket(bid.getBytes(), bid.length(), userAddress,
                                BROADCAST_PORT);
                        socket.send(packet);

                        InetAddress broadcastAddress = InetAddress.getByName(BROADCAST_ADDRESS);
                        String ended = "ended:" + auction.fileName;
                        DatagramPacket endedPacket = new DatagramPacket(ended.getBytes(), ended.length(),
                                broadcastAddress,
                                BROADCAST_PORT);
                        socket.send(endedPacket);
                    } catch (SocketTimeoutException e) {
                        System.out.println("Nachricht nicht gesendet oder empfangen: Timeout");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    if (winner != null) {
                        sendFileToWinner(auction.fileName, winner);
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
        int sum = 0;
        for (Bid aBid : bids.values()) {
            sum += aBid.bid;
        }
        System.out.println("Balance: " + balance + " | Bidding: " + sum + " | Rest: " + (balance - sum));
    }

    public static void bieten(int price, String username, String filename) {
        for (Bid bid : bids.values()) {
            if (bid.fileName.equals(filename)) {
                if (price <= bid.bid) {
                    System.out.println("Das Gebot darf nicht niedriger als dein letztes Gebot sein.");
                    return;
                }
            }
        }
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
            Bid bidInfo = new Bid(username, price, filename);
            bids.put(filename, bidInfo);
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
        try (DatagramSocket socket = new DatagramSocket()) {
            try (BufferedReader br = new BufferedReader(new FileReader("dateien/" + fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    byte[] data = ("File:" + line).getBytes();
                    DatagramPacket packet = new DatagramPacket(data, data.length,
                            InetAddress.getByName(findUser(winnerAddress)), BROADCAST_PORT);
                    socket.send(packet);
                }
                byte[] data = ("File:EOF").getBytes();
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName(findUser(winnerAddress)), BROADCAST_PORT);
                socket.send(packet);
                System.out.println("Datei gesendet.");

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void sendMoney(DatagramSocket requestSocket, String fileNameWon, int priceWon) throws IOException {
        String money = "Geld:" + priceWon;
        String winningSeller = bids.get(fileNameWon).seller;
        InetAddress sellerAddress = InetAddress.getByName(findUser(winningSeller));
        DatagramPacket moneyPacket = new DatagramPacket(money.getBytes(), money.length(),
                sellerAddress,
                BROADCAST_PORT);
        requestSocket.send(moneyPacket);
        System.out.println("Geld gesendet: " + priceWon);
        balance -= priceWon;
    }

    static class Auction {
        String fileName;
        int minPrice;
        LocalTime expiryTime;
        String seller;
        String filePath;
        String highestBidder;
        int highestBid;
        boolean ongoing = false;
        boolean canceled = false;

        public Auction(String fileName, int minPrice, LocalTime expiryTime, String seller, String filePath) {
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
        int bid;
        String fileName;
        boolean won = false;

        public Bid(String seller, int bid, String fileName) {
            this.seller = seller;
            this.bid = bid;
            this.fileName = fileName;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Bitte geben Sie Ihr Startguthaben ein.");
            return;
        }
        File folder = new File("dateien"); // Replace with your folder path

        if (folder.exists() && folder.isDirectory()) {
            System.out.println("Verzeichnis 'dateien' schon existiert.");
        } else {
            boolean created = folder.mkdirs(); // Creates the directory and any parent directories if needed
            if (created) {
                System.out.println("Verzeichnis 'dateien' erfolgreich angelegt.");
            } else {
                System.out.println("Verzeichnis nicht erfolgreich angelegt.");
            }
        }

        System.out.println("\nWarte auf Nachrichten...");

        balance = Integer.parseInt(args[0]);
        CLIListener.setName("CLIListener");
        CLIListener.start();
        requestListener.setName("RequestListener");
        requestListener.start();
    }
}
