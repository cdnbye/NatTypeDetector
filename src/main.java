import com.cdnbye.core.nat.StunClient;
import com.cdnbye.core.nat.StunResult;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class main {

    public static void main(String[] args) throws Exception {

//        String stunHost = "stun.miwifi.com";
//        String stunHost = "stun.syncthing.net";
//        int stunPort = 3478;
        String localIP = InetAddress.getLocalHost().getHostAddress();
        try {
            StunResult result = StunClient.query(localIP);
            System.out.println("Nat type: " + result.getNatType());
            System.out.println("Public IP: " + result.getIpAddr());
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }

}
