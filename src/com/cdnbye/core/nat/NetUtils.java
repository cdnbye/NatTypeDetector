package com.cdnbye.core.nat;

import java.net.*;
import java.security.InvalidParameterException;

public class NetUtils {

    public static boolean isPrivateIP(InetAddress ip) {

        if (ip == null)
        {
            throw new InvalidParameterException(ip.getHostAddress() + " is null");
        }


        byte[] ipBytes = ip.getAddress();

        /* Private IPs:
            First Octet = 192 AND Second Octet = 168 (Example: 192.168.X.X)
            First Octet = 172 AND (Second Octet >= 16 AND Second Octet <= 31) (Example: 172.16.X.X - 172.31.X.X)
            First Octet = 10 (Example: 10.X.X.X)
            First Octet = 169 AND Second Octet = 254 (Example: 169.254.X.X)

        */

        if (ipBytes[0] == 192 && ipBytes[1] == 168)
        {
            return true;
        }
        if (ipBytes[0] == 172 && ipBytes[1] >= 16 && ipBytes[1] <= 31)
        {
            return true;
        }
        if (ipBytes[0] == 10)
        {
            return true;
        }
        if (ipBytes[0] == 169 && ipBytes[1] == 254)
        {
            return true;
        }

        return false;

    }

    private static final String DEFAULT_LOCAL_END = "0.0.0.0";

    public static InetSocketAddress ParseIpAddress(String str)
    {
        String[] ipPort = str.trim().split(":");

        if (ipPort.length == 2)
        {
            return new InetSocketAddress(ipPort[0], Integer.parseInt(ipPort[1]));
        } else if (ipPort.length == 1) {
            return new InetSocketAddress(DEFAULT_LOCAL_END, Integer.parseInt(ipPort[0]));
        }

        return null;
    }

    public static DatagramSocket createSocket(InetSocketAddress addr) throws SocketException {

        return new DatagramSocket(addr);

    }

}
