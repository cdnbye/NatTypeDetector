package com.cdnbye.core.nat;

import java.net.*;
import java.security.InvalidParameterException;

public class Utils {

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

    public static byte[] ipToBytes(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip).getAddress();
    }

}
