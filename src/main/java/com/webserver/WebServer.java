package com.webserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;

public class WebServer {
    private HttpServer server;
    private final int port;
    private boolean isRunning = false;
    private String localIpAddress = "127.0.0.1";

    public WebServer(int port) {
        this.port = port;

        try {
            this.localIpAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.err.println("⚠ 无法获取本机IP地址，使用默认 127.0.0.1");
        }
    }

    public boolean start() {
        try {
            if (isRunning) {
                System.out.println("ℹ Web服务器已在运行中");
                return true;
            }

            File indexFile = new File("index.html");
            if (!indexFile.exists()) {
                System.err.println("✗ 错误: 同目录下未找到 index.html 文件");
                return false;
            }

            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new FileHandler());
            server.setExecutor(null);
            server.start();

            isRunning = true;
            System.out.println("✓ Web服务器已启动: http://" + localIpAddress + ":" + port);
            System.out.println("✓ 同时也可以通过: http://localhost:" + port + " 访问");
            return true;

        } catch (IOException e) {
            System.err.println("✗ 启动Web服务器失败: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (server != null && isRunning) {
            server.stop(0);
            isRunning = false;
            System.out.println("✓ Web服务器已停止");
        }
    }

    public void restart() {
        System.out.println("🔄 正在重启Web服务器...");
        stop();
        start();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return port;
    }

    public String getLocalIpAddress() {
        return localIpAddress;
    }

    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            String filename = requestPath.equals("/") ? "index.html" : requestPath.substring(1);

            File file = new File(filename);
            if (file.exists() && !file.isDirectory()) {
                String mimeType = getMimeType(filename);
                exchange.getResponseHeaders().set("Content-Type", mimeType);
                exchange.sendResponseHeaders(200, file.length());
                Files.copy(file.toPath(), exchange.getResponseBody());
                exchange.getResponseBody().close();

                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                System.out.println("📁 来自 " + clientIp + " 的请求: " + filename + " (" + mimeType + ")");
            } else {
                String response = "404 - 文件未找到";
                exchange.sendResponseHeaders(404, response.getBytes().length);
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("❌ 文件未找到: " + filename);
            }
        }

        private String getMimeType(String filename) {
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(".html")) return "text/html";
            if (lowerFilename.endsWith(".css")) return "text/css";
            if (lowerFilename.endsWith(".js")) return "application/javascript";
            if (lowerFilename.endsWith(".png")) return "image/png";
            if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) return "image/jpeg";
            if (lowerFilename.endsWith(".gif")) return "image/gif";
            if (lowerFilename.endsWith(".json")) return "application/json";
            if (lowerFilename.endsWith(".ico")) return "image/x-icon";
            return "text/plain";
        }
    }
}