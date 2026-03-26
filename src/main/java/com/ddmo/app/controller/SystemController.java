package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ServletWebServerApplicationContext webServerApplicationContext;

    public SystemController(ServletWebServerApplicationContext webServerApplicationContext) {
        this.webServerApplicationContext = webServerApplicationContext;
    }

    @GetMapping("/access-info")
    public ApiResponse<Map<String, Object>> accessInfo() {
        int port = webServerApplicationContext.getWebServer().getPort();
        String ip = resolveHostIp();
        Map<String, Object> data = new HashMap<>();
        data.put("ip", ip);
        data.put("port", port);
        data.put("url", "http://" + ip + ":" + port);
        return ApiResponse.ok(data);
    }

    private String resolveHostIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface netIf = interfaces.nextElement();
                if (!netIf.isUp() || netIf.isLoopback() || netIf.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = netIf.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "127.0.0.1";
        }
    }
}

