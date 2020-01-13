package com.cdnbye.core.nat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.InvalidParameterException;
import java.util.Arrays;

public class StunClient {

    private static final int UDP_SEND_COUNT = 3;
    private static final int TRANSACTION_TIMEOUT = 1000;
    private static final int SEND_PORT = 50899;

    // Gets NAT info from STUN server.
    public static StunResult query(String stunHost, int stunPort, String localIP) throws SocketException {
        if (stunHost == null)
        {
            throw new InvalidParameterException("host is null");
        }
        if (localIP == null)
        {
            throw new InvalidParameterException("localIP is null");
        }

        InetSocketAddress localEP = new InetSocketAddress(SEND_PORT);
        DatagramSocket s = new DatagramSocket(localEP);
        return query(stunHost, stunPort, s, localIP);
    }

    // Gets NAT info from STUN server. Returns UDP network info.
    public static StunResult query(String stunHost, int stunPort, DatagramSocket socket, String localIP)
    {
        if (stunHost == null)
        {
            throw new InvalidParameterException("host is null");
        }
        if (socket == null)
        {
            throw new InvalidParameterException("socket is null");
        }
        if (stunPort < 1)
        {
            throw new InvalidParameterException("Port value must be >= 1 !");
        }

        InetSocketAddress remoteEndPoint = new InetSocketAddress(stunHost, stunPort);

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
            StunMessage test1Response = doTransaction(test1, socket, remoteEndPoint, TRANSACTION_TIMEOUT);
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
                if (Arrays.equals(Utils.ipToBytes(localIP), test1Response.getMappedAddress().getAddress().getAddress()))
                {
                    // IP相同
                    StunMessage test2Response = doTransaction(test2, socket, remoteEndPoint, TRANSACTION_TIMEOUT);
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
                    StunMessage test2Response = doTransaction(test2, socket, remoteEndPoint, TRANSACTION_TIMEOUT);

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
                        StunMessage test12Response = doTransaction(test12, socket, test1Response.getChangedAddress(), TRANSACTION_TIMEOUT);
                        if (test12Response == null)
                        {
                            throw new Exception("STUN Test I(II) didn't get response !");
                        }
                        else
                        {
                            // Symmetric NAT
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

                                StunMessage test3Response = doTransaction(test3, socket, test1Response.getChangedAddress(), TRANSACTION_TIMEOUT);
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
            return new StunResult(NatType.Unknown, null);
        }
        finally
        {
            socket.close();
        }
    }

    // Does STUN transaction. Returns transaction response or null if transaction failed.
    // Returns transaction response or null if transaction failed.
    private static StunMessage doTransaction(StunMessage request, DatagramSocket socket, InetSocketAddress remoteEndPoint, int timeout) throws Exception
    {
        long t1 = System.currentTimeMillis();
        byte[] requestBytes = request.toByteData();

//        System.out.println("remoteEndPoint " + remoteEndPoint);
        if (request.getChangeRequest() != null) {
//            System.out.println("request isChangePort " + request.getChangeRequest().isChangePort() + " isChangeIp " + request.getChangeRequest().isChangeIp());
        }
//        System.out.println("request TransactionId " + new String(request.getTransactionId()));
//            System.out.println("socket.send");
        socket.setSoTimeout(timeout);
        DatagramPacket dp = new DatagramPacket(requestBytes, requestBytes.length, remoteEndPoint);
        boolean revResponse = false;                   // 是否接收到数据的标志位
        int receiveCount = 0;
        byte[] receiveBuffer = new byte[512];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
        StunMessage response = new StunMessage();
        while (!revResponse && receiveCount < UDP_SEND_COUNT) {
            try {
                socket.send(dp);
                socket.receive(receivePacket);
                // parse message
                response.parse(receiveBuffer);
                // Check that transaction ID matches or not response what we want.
                if (Arrays.equals(request.getTransactionId(), response.getTransactionId())) {
                    revResponse = true;
                } else {
                    System.out.println("TransactionId not match!");
                    throw new Exception("TransactionId not match!");
                }
            } catch (IOException e) {

                e.printStackTrace();
            } finally {
                receiveCount ++;
            }
        }

        long t2 = System.currentTimeMillis();
        System.out.println("doTransaction time " + (t2-t1));

        if (revResponse) {
            return response;
        } else {
            return null;
        }

    }
}
