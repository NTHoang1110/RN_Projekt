import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MIbayCLI {
    private static final int CLIPORT = 12345;

    public static void main(String[] args) throws IOException {
        InetAddress address = InetAddress.getLocalHost();
        if (args.length == 0) {
            return;
        }
        String command = args[0];
        try (DatagramSocket socket = new DatagramSocket()) {
            String response;
            switch (command) {
                case "anbieten":
                    response = "anbieten" + " " + args[1] + " " + args[2] + " " + args[3];
                    break;
                case "bieten":
                    response = "bieten" + " " + args[1] + " " + args[2] + " " + args[3];
                    break;
                case "abbrechen":
                    response = "abbrechen" + " " + args[1];
                    break;
                case "liste":
                    response = "liste";
                    break;
                case "info":
                    response = "info";
                    break;
                default:
                    System.out.println("Unbekannter Befehl");
                    response = "";
            }
            DatagramPacket packet = new DatagramPacket(response.getBytes(), response.length(), address,
                    CLIPORT);
            socket.send(packet);
        }
    }
}
