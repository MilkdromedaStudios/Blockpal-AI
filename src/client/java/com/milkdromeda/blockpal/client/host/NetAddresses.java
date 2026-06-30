package com.milkdromeda.blockpal.client.host;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Works out the addresses friends use to connect:
 * <ul>
 *   <li><b>Local</b> (LAN) — the site-local IPv4 of a real network interface.</li>
 *   <li><b>Public</b> — what the internet sees, via an IP-echo service. This is the
 *       host's own address, so the UI warns before showing it.</li>
 * </ul>
 * Note: knowing the public IP does <i>not</i> make the host reachable — inbound
 * still needs port-forwarding (or a tunnel). The UI says so.
 */
final class NetAddresses {

    private NetAddresses() {}

    static String localIp() {
        try {
            for (Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements();) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (Enumeration<InetAddress> addrs = ni.getInetAddresses(); addrs.hasMoreElements();) {
                    InetAddress a = addrs.nextElement();
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            // fall through to loopback
        }
        return "127.0.0.1";
    }

    static String publicIp() {
        try {
            return Http.getString("https://checkip.amazonaws.com").trim();
        } catch (Exception e) {
            return "(unknown — look it up with \"what is my IP\")";
        }
    }
}
