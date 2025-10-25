package com.webserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WebStatusMonitor {
    private final ConfigManager configManager;
    private final WebServer webServer;
    private ScheduledExecutorService scheduler;
    private boolean monitoring = false;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    private final AtomicInteger totalChecks = new AtomicInteger(0);
    private String localIpAddress = "127.0.0.1";

    public WebStatusMonitor(ConfigManager configManager, WebServer webServer) {
        this.configManager = configManager;
        this.webServer = webServer;

        try {
            this.localIpAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.err.println("⚠ 无法获取本机IP地址，使用默认 127.0.0.1");
        }
    }

    public void startMonitoring() {
        if (monitoring) {
            System.out.println("ℹ Web状态监控已在运行中");
            return;
        }

        monitoring = true;
        scheduler = Executors.newSingleThreadScheduledExecutor();

        System.out.println("✓ Web状态监控已启动 (间隔: 5秒)");
        System.out.println("📡 本机IP地址: " + localIpAddress);

        scheduler.scheduleAtFixedRate(this::checkWebStatus, 0, 5, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        monitoring = false;
        System.out.println("✓ Web状态监控已停止");
    }

    private void checkWebStatus() {
        String monitorUrl = configManager.getMonitorWebStatus();
        String targetUrl;
        boolean isLocal = false;

        if (monitorUrl.isEmpty()) {
            targetUrl = "http://" + localIpAddress + ":" + webServer.getPort();
            isLocal = true;
        } else {
            targetUrl = monitorUrl;
            if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                targetUrl = "http://" + targetUrl;
            }
        }

        try {
            URL url = new URL(targetUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "WebServerMonitor/1.0");

            long startTime = System.currentTimeMillis();
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;

            long contentLength = connection.getContentLengthLong();
            String contentType = connection.getContentType();
            String serverHeader = connection.getHeaderField("Server");

            totalResponseTime.addAndGet(responseTime);
            totalChecks.incrementAndGet();
            consecutiveFailures.set(0);

            String statusReport = generateStatusReport(
                    targetUrl, responseCode, responseTime, contentLength,
                    contentType, serverHeader, isLocal, true
            );

            System.out.println(statusReport);

            connection.disconnect();

        } catch (Exception e) {
            consecutiveFailures.incrementAndGet();
            totalChecks.incrementAndGet();

            String statusReport = generateStatusReport(
                    targetUrl, -1, -1, -1,
                    null, null, isLocal, false
            );

            System.out.println(statusReport);
        }
    }

    private String generateStatusReport(String url, int responseCode, long responseTime,
                                        long contentLength, String contentType,
                                        String serverHeader, boolean isLocal, boolean success) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String timestamp = sdf.format(new Date());

        StringBuilder report = new StringBuilder();
        report.append("=== Web状态监控报告 ===\n");
        report.append("时间: ").append(timestamp).append("\n");
        report.append("目标: ").append(url);
        if (isLocal) {
            report.append(" (本地服务器)");
        }
        report.append("\n");

        if (success) {
            report.append("状态: ✓ 正常\n");
            report.append("响应码: ").append(responseCode).append(" ").append(getHttpStatusText(responseCode)).append("\n");
            report.append("响应时间: ").append(responseTime).append("ms\n");

            if (contentLength >= 0) {
                report.append("内容长度: ");
                if (contentLength < 1024) {
                    report.append(contentLength).append(" B\n");
                } else if (contentLength < 1024 * 1024) {
                    report.append(String.format("%.2f KB", contentLength / 1024.0)).append("\n");
                } else {
                    report.append(String.format("%.2f MB", contentLength / (1024.0 * 1024.0))).append("\n");
                }
            }

            if (contentType != null) {
                report.append("内容类型: ").append(contentType).append("\n");
            }

            if (serverHeader != null) {
                report.append("服务器: ").append(serverHeader).append("\n");
            }
        } else {
            report.append("状态: ✗ 异常\n");
            report.append("错误: 连接失败或超时\n");
            report.append("连续失败次数: ").append(consecutiveFailures.get()).append("\n");
        }

        if (totalChecks.get() > 0) {
            double avgResponseTime = (double) totalResponseTime.get() / totalChecks.get();
            report.append("--- 统计信息 ---\n");
            report.append("总检查次数: ").append(totalChecks.get()).append("\n");
            report.append("平均响应时间: ").append(String.format("%.2f", avgResponseTime)).append("ms\n");
            double successRate = ((double) (totalChecks.get() - consecutiveFailures.get()) / totalChecks.get()) * 100;
            report.append("成功率: ").append(String.format("%.2f", successRate)).append("%\n");
        }

        report.append("=====================");

        return report.toString();
    }

    private String getHttpStatusText(int statusCode) {
        switch (statusCode) {
            case 200: return "OK";
            case 301: return "Moved Permanently";
            case 302: return "Found";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            default: return "Unknown";
        }
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    public void restartMonitoring() {
        System.out.println("🔄 重新启动Web状态监控...");
        stopMonitoring();
        startMonitoring();
    }

    public String getLocalIpAddress() {
        return localIpAddress;
    }
}