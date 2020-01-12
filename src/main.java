import com.cdnbye.core.nat.StunClient;
import com.cdnbye.core.nat.StunResult;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

public class main {

    public static void main(String[] args) {

//        try {
//            InetAddress addr = InetAddress.getByName("111.13.100.91");
//            System.out.println(addr.getAddress()[3] == 91);
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        }


//        long t1 = System.currentTimeMillis();
//        try {
//            Thread.sleep(1000);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        System.out.println(System.currentTimeMillis() - t1);
//        String stunHost = "stun.miwifi.com";
        String stunHost = "stun.syncthing.net";
        int stunPort = 3478;
        InetSocketAddress localEP = new InetSocketAddress(50899);
        try {
            StunResult result = StunClient.query(stunHost, stunPort, localEP);
            System.out.println("Nat type: " + result.getNatType());
            System.out.println("Public IP: " + result.getIpAddr());
        } catch (SocketException e) {
            e.printStackTrace();
        }


    }

}
