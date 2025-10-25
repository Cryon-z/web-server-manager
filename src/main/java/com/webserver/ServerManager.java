package com.webserver;

import java.util.Scanner;
import java.io.*;

public class ServerManager {
    private final ConfigManager configManager;
    private final WebServer webServer;
    private final ScriptRunner scriptRunner;
    private final WebStatusMonitor webMonitor;
    private boolean running = true;

    public ServerManager() {
        // 确保必要的资源文件存在
        ensureResources();

        configManager = new ConfigManager();
        int port = configManager.getWebPort();
        webServer = new WebServer(port);
        scriptRunner = new ScriptRunner();
        webMonitor = new WebStatusMonitor(configManager, webServer);
    }

    /**
     * 确保必要的资源文件存在，如果不存在则从JAR中提取
     */
    private void ensureResources() {
        File indexFile = new File("index.html");
        if (!indexFile.exists()) {
            System.out.println("ℹ 未找到index.html，从JAR资源中提取...");
            extractResource("index.html", "index.html");
        }

        // 可以在这里添加其他需要提取的资源文件
    }

    /**
     * 从JAR中提取资源文件到当前目录
     */
    private void extractResource(String resourcePath, String outputPath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
             FileOutputStream outputStream = new FileOutputStream(outputPath)) {

            if (inputStream == null) {
                System.err.println("✗ 无法找到资源文件: " + resourcePath);
                return;
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            System.out.println("✓ 已提取资源文件: " + outputPath);

        } catch (IOException e) {
            System.err.println("✗ 提取资源文件失败: " + e.getMessage());
        }
    }

    public void start() {
        printBanner();

        webServer.start();

        if (configManager.isStartRunEnabled()) {
            scriptRunner.start();
        }

        webMonitor.startMonitoring();

        startCommandListener();
    }

    private void printBanner() {
        int port = configManager.getWebPort();
        String localIp = webServer.getLocalIpAddress();
        boolean startRunEnabled = configManager.isStartRunEnabled();
        String monitorTarget = configManager.getMonitorWebStatus();

        if (monitorTarget.isEmpty()) {
            monitorTarget = localIp + ":" + port;
        }

        System.out.println("=== Web服务器与脚本管理器 ===");
        System.out.println("访问地址: http://" + localIp + ":" + port);
        System.out.println("自动运行脚本: " + startRunEnabled);
        System.out.println("监控目标: " + monitorTarget);
        System.out.println("输入 'help' 查看可用命令");
        System.out.println("=============================");
    }

    private void startCommandListener() {
        Scanner scanner = new Scanner(System.in);

        while (running) {
            try {
                System.out.print("> ");
                String command = scanner.nextLine().trim();

                if (command.isEmpty()) {
                    continue;
                }

                String[] parts = command.split(" ", 2);
                String mainCommand = parts[0].toLowerCase();
                String argument = parts.length > 1 ? parts[1] : "";

                switch (mainCommand) {
                    case "help":
                        showHelp();
                        break;
                    case "restart":
                        if (argument.isEmpty()) {
                            restartAll();
                        } else {
                            handleRestartWithArgument(argument);
                        }
                        break;
                    case "status":
                        showStatus();
                        break;
                    case "config":
                        handleConfigCommand(argument);
                        break;
                    case "monitor":
                        handleMonitorCommand(argument);
                        break;
                    case "info":
                        showNetworkInfo();
                        break;
                    case "exit":
                    case "quit":
                        shutdown();
                        break;
                    default:
                        System.out.println("未知命令: " + command);
                        System.out.println("输入 'help' 查看可用命令");
                }

            } catch (Exception e) {
                System.err.println("处理命令时出错: " + e.getMessage());
            }
        }
        scanner.close();
    }

    private void showHelp() {
        System.out.println("可用命令:");
        System.out.println("  restart                    - 重启Web服务器和脚本");
        System.out.println("  restart web               - 仅重启Web服务器");
        System.out.println("  restart run               - 仅重启脚本");
        System.out.println("  restart monitor           - 仅重启Web状态监控");
        System.out.println("  status                    - 显示当前状态");
        System.out.println("  config show               - 显示当前配置");
        System.out.println("  config set <key> <value>  - 修改配置项");
        System.out.println("  monitor status            - 显示监控状态");
        System.out.println("  monitor restart           - 重启监控");
        System.out.println("  monitor stop              - 停止监控");
        System.out.println("  monitor start             - 启动监控");
        System.out.println("  info                      - 显示网络信息");
        System.out.println("  help                      - 显示此帮助信息");
        System.out.println("  exit/quit                 - 退出程序");
    }

    private void showNetworkInfo() {
        String localIp = webServer.getLocalIpAddress();
        int port = webServer.getPort();

        System.out.println("=== 网络信息 ===");
        System.out.println("本机IP地址: " + localIp);
        System.out.println("服务端口: " + port);
        System.out.println("访问地址:");
        System.out.println("  http://" + localIp + ":" + port);
        System.out.println("  http://localhost:" + port);
        System.out.println("  http://127.0.0.1:" + port);
    }

    private void handleRestartWithArgument(String argument) {
        switch (argument.toLowerCase()) {
            case "web":
                restartWeb();
                break;
            case "run":
                restartRun();
                break;
            case "monitor":
                restartMonitor();
                break;
            default:
                System.out.println("未知的重启目标: " + argument);
                System.out.println("可用目标: web, run, monitor");
        }
    }

    private void handleConfigCommand(String argument) {
        if (argument.isEmpty()) {
            System.out.println("config 命令用法:");
            System.out.println("  config show               - 显示当前配置");
            System.out.println("  config set <key> <value>  - 修改配置项");
            return;
        }

        String[] parts = argument.split(" ", 2);
        String subCommand = parts[0].toLowerCase();

        switch (subCommand) {
            case "show":
                showConfig();
                break;
            case "set":
                if (parts.length > 1) {
                    String[] keyValue = parts[1].split(" ", 2);
                    if (keyValue.length == 2) {
                        setConfig(keyValue[0], keyValue[1]);
                    } else {
                        System.out.println("配置设置格式错误");
                        System.out.println("正确格式: config set <key> <value>");
                    }
                }
                break;
            default:
                System.out.println("未知的config子命令: " + subCommand);
        }
    }

    private void handleMonitorCommand(String argument) {
        if (argument.isEmpty()) {
            System.out.println("monitor 命令用法:");
            System.out.println("  monitor status   - 显示监控状态");
            System.out.println("  monitor restart  - 重启监控");
            System.out.println("  monitor stop     - 停止监控");
            System.out.println("  monitor start    - 启动监控");
            return;
        }

        switch (argument.toLowerCase()) {
            case "status":
                System.out.println("Web状态监控: " + (webMonitor.isMonitoring() ? "运行中" : "已停止"));
                break;
            case "restart":
                restartMonitor();
                break;
            case "stop":
                webMonitor.stopMonitoring();
                break;
            case "start":
                webMonitor.startMonitoring();
                break;
            default:
                System.out.println("未知的monitor子命令: " + argument);
        }
    }

    private void showConfig() {
        System.out.println("=== 当前配置 ===");
        System.out.println("web_port: " + configManager.getWebPort());
        System.out.println("enable_start_run: " + configManager.isStartRunEnabled());
        String monitorUrl = configManager.getMonitorWebStatus();
        if (monitorUrl.isEmpty()) {
            monitorUrl = webServer.getLocalIpAddress() + ":" + webServer.getPort();
        }
        System.out.println("monitor_web_status: " + monitorUrl);
    }

    private void setConfig(String key, String value) {
        switch (key.toLowerCase()) {
            case "web_port":
                try {
                    int port = Integer.parseInt(value);
                    if (port < 1 || port > 65535) {
                        System.out.println("端口号必须在1-65535之间");
                        return;
                    }
                    configManager.setWebPort(port);
                    System.out.println("已设置web_port为: " + port);
                    System.out.println("需要重启Web服务器使新端口生效");
                } catch (NumberFormatException e) {
                    System.out.println("端口号必须是数字");
                }
                break;
            case "enable_start_run":
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    boolean enabled = Boolean.parseBoolean(value);
                    configManager.setStartRunEnabled(enabled);
                    System.out.println("已设置enable_start_run为: " + enabled);
                } else {
                    System.out.println("enable_start_run必须是 true 或 false");
                }
                break;
            case "monitor_web_status":
                configManager.setMonitorWebStatus(value);
                System.out.println("已设置monitor_web_status为: " + value);
                System.out.println("需要重启监控使新配置生效");
                break;
            default:
                System.out.println("未知的配置项: " + key);
                System.out.println("可用配置项: web_port, enable_start_run, monitor_web_status");
        }
    }

    private void restartAll() {
        System.out.println("正在重启Web服务器和脚本...");
        restartWeb();
        restartRun();
        restartMonitor();
    }

    private void restartWeb() {
        webServer.restart();
    }

    private void restartRun() {
        scriptRunner.restart();
    }

    private void restartMonitor() {
        webMonitor.restartMonitoring();
    }

    private void showStatus() {
        System.out.println("当前状态:");
        System.out.println("  Web服务器: " + (webServer.isRunning() ? "运行中 (端口 " + webServer.getPort() + ")" : "已停止"));
        System.out.println("  脚本运行: " + (scriptRunner.isRunning() ? "运行中" : "已停止"));
        System.out.println("  Web监控: " + (webMonitor.isMonitoring() ? "运行中" : "已停止"));
        System.out.println("  自动运行: " + (configManager.isStartRunEnabled() ? "启用" : "禁用"));

        String monitorTarget = configManager.getMonitorWebStatus();
        if (monitorTarget.isEmpty()) {
            monitorTarget = webServer.getLocalIpAddress() + ":" + webServer.getPort();
        }
        System.out.println("  监控目标: " + monitorTarget);
    }

    private void shutdown() {
        System.out.println("正在关闭服务器...");
        running = false;
        webServer.stop();
        scriptRunner.stop();
        webMonitor.stopMonitoring();
        System.out.println("服务器已关闭");
    }

    public static void main(String[] args) {
        ServerManager manager = new ServerManager();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n收到关闭信号，正在清理资源...");
            manager.webServer.stop();
            manager.scriptRunner.stop();
            manager.webMonitor.stopMonitoring();
            System.out.println("资源清理完成");
        }));

        manager.start();
    }
}