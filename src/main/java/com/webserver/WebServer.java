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
            server.createContext("/upload", new UploadHandler(this)); // 传递WebServer实例
            server.setExecutor(null);
            server.start();

            isRunning = true;
            System.out.println("✓ Web服务器已启动: http://" + localIpAddress + ":" + port);
            System.out.println("✓ 同时也可以通过: http://localhost:" + port + " 访问");
            System.out.println("✓ 文件上传功能已启用: http://" + localIpAddress + ":" + port + "/upload");
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

    // 文件上传处理器 - 改为非静态内部类
    class UploadHandler implements HttpHandler {
        private final WebServer webServer;

        public UploadHandler(WebServer webServer) {
            this.webServer = webServer;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"success\": false, \"message\": \"方法不允许\"}");
                return;
            }

            try {
                // 解析 multipart/form-data 请求
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"无效的内容类型\"}");
                    return;
                }

                // 读取请求体
                InputStream requestBody = exchange.getRequestBody();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = requestBody.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] requestData = buffer.toByteArray();

                // 解析 multipart 数据
                String boundary = extractBoundary(contentType);
                if (boundary == null) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"无效的边界\"}");
                    return;
                }

                // 提取文件内容
                byte[] fileContent = extractFileContent(requestData, boundary.getBytes());
                if (fileContent == null || fileContent.length == 0) {
                    sendResponse(exchange, 400, "{\"success\": false, \"message\": \"未找到文件内容\"}");
                    return;
                }

                // 第一步：将上传的文件保存为 index-update.html
                File updateFile = new File("index-update.html");
                try (FileOutputStream fos = new FileOutputStream(updateFile)) {
                    fos.write(fileContent);
                }

                // 第二步：删除当前的 index.html（如果存在）
                File currentIndex = new File("index.html");
                if (currentIndex.exists()) {
                    if (!currentIndex.delete()) {
                        sendResponse(exchange, 500, "{\"success\": false, \"message\": \"无法删除当前index.html\"}");
                        return;
                    }
                }

                // 第三步：将 index-update.html 重命名为 index.html
                if (!updateFile.renameTo(new File("index.html"))) {
                    sendResponse(exchange, 500, "{\"success\": false, \"message\": \"无法重命名文件\"}");
                    return;
                }

                // 发送成功响应
                String response = "{\"success\": true, \"message\": \"文件上传成功，服务器将重启\"}";
                sendResponse(exchange, 200, response);

                // 记录上传日志
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                System.out.println("📤 来自 " + clientIp + " 的文件上传成功，已替换index.html");

                // 在单独的线程中重启服务器，避免阻塞响应
                new Thread(() -> {
                    try {
                        Thread.sleep(1000); // 等待1秒确保响应已发送
                        webServer.restart(); // 使用传入的webServer实例
                    } catch (Exception e) {
                        System.err.println("重启服务器时出错: " + e.getMessage());
                    }
                }).start();

            } catch (Exception e) {
                System.err.println("处理文件上传时出错: " + e.getMessage());
                sendResponse(exchange, 500, "{\"success\": false, \"message\": \"服务器错误: " + e.getMessage() + "\"}");
            }
        }

        private String extractBoundary(String contentType) {
            String[] parts = contentType.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith("boundary=")) {
                    return part.substring("boundary=".length());
                }
            }
            return null;
        }

        private byte[] extractFileContent(byte[] requestData, byte[] boundary) {
            // 查找文件内容的开始和结束位置
            byte[] startPattern = ("\r\n\r\n").getBytes();
            byte[] endPattern = ("\r\n--" + new String(boundary)).getBytes();

            int startIndex = indexOf(requestData, startPattern, 0);
            if (startIndex == -1) return null;
            startIndex += startPattern.length;

            int endIndex = indexOf(requestData, endPattern, startIndex);
            if (endIndex == -1) return null;

            // 提取文件内容
            byte[] fileContent = new byte[endIndex - startIndex];
            System.arraycopy(requestData, startIndex, fileContent, 0, fileContent.length);
            return fileContent;
        }

        private int indexOf(byte[] source, byte[] target, int fromIndex) {
            if (fromIndex >= source.length) return -1;
            if (target.length == 0) return fromIndex;

            byte first = target[0];
            int max = source.length - target.length;

            for (int i = fromIndex; i <= max; i++) {
                if (source[i] != first) continue;

                boolean found = true;
                for (int j = 1; j < target.length; j++) {
                    if (source[i + j] != target[j]) {
                        found = false;
                        break;
                    }
                }
                if (found) return i;
            }
            return -1;
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(statusCode, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}