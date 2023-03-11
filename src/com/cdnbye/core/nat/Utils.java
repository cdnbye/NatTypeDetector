package com.cdnbye.core.nat;

import java.net.*;
import java.security.InvalidParameterException;

public class Utils {

    private static final String DEFAULT_LOCAL_END = "0.0.0.0";

    public static byte[] ipToBytes(String ip) throws UnknownHostException {
        return InetAddress.getByName(ip).getAddress();
    }

}
