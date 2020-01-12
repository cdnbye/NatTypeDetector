package com.cdnbye.core.nat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StunClient {

    private static final int UDP_SEND_COUNT = 3;
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    // Gets NAT info from STUN server.
    public static StunResult query(String host, int port, InetSocketAddress localEP) throws SocketException {
        if (host == null)
        {
            throw new InvalidParameterException("host is null");
        }
        if (localEP == null)
        {
            throw new InvalidParameterException("localEP is null");
        }

        DatagramSocket s = new DatagramSocket(localEP);
        return query(host, port, s);
    }

    // Gets NAT info from STUN server. Returns UDP network info.
    public static StunResult query(String host, int port, DatagramSocket socket)
    {
        if (host == null)
        {
            throw new InvalidParameterException("host is null");
        }
        if (socket == null)
        {
            throw new InvalidParameterException("socket is null");
        }
        if (port < 1)
        {
            throw new InvalidParameterException("Port value must be >= 1 !");
        }

        InetSocketAddress remoteEndPoint = new InetSocketAddress(host, port);

			/*
                In test I, the client sends a STUN Binding Request to a server, without any flags set in the
                CHANGE-REQUEST attribute, and without the RESPONSE-ADDRESS attribute. This causes the server
                to send the response back to the address and port that the request came from.

                In test II, the client sends a Binding Request with both the "change IP" and "change port" flags
                from the CHANGE-REQUEST attribute set.

                In test III, the client sends a Binding Request with only the "change port" flag set.

                                    +--------+
                                    |  Test  |
                                    |   I    |
                                    +--------+
                                         |
                                         |
                                         V
                                        /\              /\
                                     N /  \ Y          /  \ Y             +--------+
                      UDP     <-------/Resp\--------->/ IP \------------->|  Test  |
                      Blocked         \ ?  /          \Same/              |   II   |
                                       \  /            \? /               +--------+
                                        \/              \/                    |
                                                         | N                  |
                                                         |                    V
                                                         V                    /\
                                                     +--------+  Sym.      N /  \
                                                     |  Test  |  UDP    <---/Resp\
                                                     |   II   |  Firewall   \ ?  /
                                                     +--------+              \  /
                                                         |                    \/
                                                         V                     |Y
                              /\                         /\                    |
               Symmetric  N  /  \       +--------+   N  /  \                   V
                  NAT  <--- / IP \<-----|  Test  |<--- /Resp\               Open
                            \Same/      |   I    |     \ ?  /               Internet
                             \? /       +--------+      \  /
                              \/                         \/
                              |                           |Y
                              |                           |
                              |                           V
                              |                           Full
                              |                           Cone
                              V              /\
                          +--------+        /  \ Y
                          |  Test  |------>/Resp\---->Restricted
                          |   III  |       \ ?  /
                          +--------+        \  /
                                             \/
                                              |N
                                              |       Port
                                              +------>Restricted

            */

        try
        {
            // Test I
            StunMessage test1 = new StunMessage(StunMessageType.BindingRequest);
            StunMessage test1Response = doTransaction(test1, socket, remoteEndPoint, 1600);
            // UDP blocked.
            if (test1Response == null)
            {
                return new StunResult(NatType.UdpBlocked, null);
            }

            else
            {
//                System.out.println("test1Response: " + test1Response.getMappedAddress());
                // Test II
                StunMessage test2 = new StunMessage(StunMessageType.BindingRequest, new StunChangeRequest(true, true));

                // No NAT.
                if (Arrays.equals(socket.getLocalAddress().getAddress(), test1Response.getMappedAddress().getAddress().getAddress()))
                {
                    // IP相同
//                    System.out.println("No NAT.");
                    StunMessage test2Response = doTransaction(test2, socket, remoteEndPoint, 1600);
                    // Open Internet.
                    if (test2Response != null)
                    {
                        return new StunResult(NatType.OpenInternet, test1Response.getMappedAddress());
                    }
                    // Symmetric UDP firewall.
                    else
                    {
                        return new StunResult(NatType.SymmetricUdpFirewall, test1Response.getMappedAddress());
                    }
                }
                // NAT
                else
                {
//                    System.out.println("NAT");
                    StunMessage test2Response = doTransaction(test2, socket, remoteEndPoint, 1600);

                    // Full cone NAT.
                    if (test2Response != null)
                    {
                        return new StunResult(NatType.FullCone, test1Response.getMappedAddress());
                    }
                    else
                    {
							/*
                                If no response is received, it performs test I again, but this time, does so to
                                the address and port from the CHANGED-ADDRESS attribute from the response to test I.
                            */

                        // Test I(II)
//                        System.out.println("begin Test I(II)");
                        StunMessage test12 = new StunMessage(StunMessageType.BindingRequest);
//                        System.out.println("test1Response.getChangedAddress() " + test1Response.getChangedAddress());
                        StunMessage test12Response = doTransaction(test12, socket, test1Response.getChangedAddress(), 1600);
                        if (test12Response == null)
                        {
                            throw new Exception("STUN Test I(II) didn't get response !");
                        }
                        else
                        {
                            // Symmetric NAT
//                            System.out.println("test12Response " + test12Response.getMappedAddress().getPort());
//                            System.out.println("test1Response " + test1Response.getMappedAddress().getPort());
                            if (!(Arrays.equals(test12Response.getMappedAddress().getAddress().getAddress(), test1Response.getMappedAddress().getAddress().getAddress())
                            && test12Response.getMappedAddress().getPort() == test1Response.getMappedAddress().getPort()))
                            {
                                return new StunResult(NatType.Symmetric, test1Response.getMappedAddress());
                            }
                            else
                            {
                                // Test III
//                                System.out.println("begin Test III");
                                StunMessage test3 = new StunMessage(StunMessageType.BindingRequest, new StunChangeRequest(false, true));

                                StunMessage test3Response = doTransaction(test3, socket, test1Response.getChangedAddress(), 1600);
                                // Restricted
                                if (test3Response != null)
                                {
                                    return new StunResult(NatType.RestrictedCone, test1Response.getMappedAddress());
                                }
                                // Port restricted
                                else
                                {
                                    return new StunResult(NatType.PortRestrictedCone, test1Response.getMappedAddress());
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            // unknown
//            System.out.println("reach unknown");
            return new StunResult(NatType.Unknown, null);
        }
//        finally
//        {
//            // Junk all late responses.
//            long startTime = System.currentTimeMillis();
//            while (System.currentTimeMillis() - startTime < 200)
//            {
//                // We got response.
//                byte[] receiveBuffer = new byte[512];
//                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
//                try {
//                    socket.receive(receivePacket);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//            socket.close();
//        }
    }

    // Does STUN transaction. Returns transaction response or null if transaction failed.
    // Returns transaction response or null if transaction failed.
    private static StunMessage doTransaction(StunMessage request, DatagramSocket socket, InetSocketAddress remoteEndPoint, int timeout) throws IOException
    {
        final CountDownLatch latch = new CountDownLatch(2);

        byte[] requestBytes = request.toByteData();
        byte[] resultBuffer = null;
        int receiveCount = 0;
        System.out.println("remoteEndPoint " + remoteEndPoint);
        if (request.getChangeRequest() != null) {
            System.out.println("request isChangePort " + request.getChangeRequest().isChangePort() + " isChangeIp " + request.getChangeRequest().isChangeIp());
        }
        DatagramPacket dp = new DatagramPacket(requestBytes, requestBytes.length, remoteEndPoint);
//            System.out.println("socket.send");
        socket.setSoTimeout(timeout);
        for (int i=0;i<UDP_SEND_COUNT;i++) {
            socket.send(dp);
        }


        while (true) {
            // We got response.
            //创建接收缓冲区
            byte[] receiveBuffer = new byte[512];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            try {
                socket.receive(receivePacket);
            } catch (IOException e) {
                receiveCount ++;
                if (receiveCount == UDP_SEND_COUNT) {
                    break;
                } else {
                    continue;
                }
            }

            System.out.println("receivePacket.getAddress  " + receivePacket.getAddress() + " port " + receivePacket.getPort());
            resultBuffer = receiveBuffer;
            receiveCount ++;
            if (receiveCount == UDP_SEND_COUNT) break;
        }

        if (resultBuffer == null) {
            System.out.println("doTransaction timeout!");
            return null;
        }
        // parse message
        StunMessage response = new StunMessage();
        response.parse(resultBuffer);
        // Check that transaction ID matches or not response what we want.
        if (Arrays.equals(request.getTransactionId(), response.getTransactionId()))
        {
//                System.out.println("Arrays.equals");
            return response;
        } else {
            System.out.println("TransactionId not match!");
        }

        return null;
    }
}
