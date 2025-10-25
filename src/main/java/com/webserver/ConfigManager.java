package com.webserver;

import java.io.*;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "server.conf";
    private final Properties properties;

    public ConfigManager() {
        properties = new Properties();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
                System.out.println("✓ 已加载配置文件: " + CONFIG_FILE);
            } catch (IOException e) {
                System.err.println("✗ 读取配置文件失败: " + e.getMessage());
            }
        } else {
            System.out.println("ℹ 配置文件不存在，创建默认配置");
            createDefaultConfig();
        }
    }

    private void createDefaultConfig() {
        properties.setProperty("web_port", "11000");
        properties.setProperty("enable_start_run", "true");
        properties.setProperty("monitor_web_status", "");
        saveConfig();
    }

    public void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "Web服务器配置文件");
            System.out.println("✓ 配置文件已保存: " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("✗ 保存配置文件失败: " + e.getMessage());
        }
    }

    public int getWebPort() {
        try {
            String portStr = properties.getProperty("web_port", "11000").trim();
            if (portStr.isEmpty()) {
                return 11000;
            }
            return Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("✗ 端口配置无效，使用默认端口11000");
            return 11000;
        }
    }

    public boolean isStartRunEnabled() {
        String enabled = properties.getProperty("enable_start_run", "true");
        return "true".equalsIgnoreCase(enabled.trim());
    }

    public String getMonitorWebStatus() {
        return properties.getProperty("monitor_web_status", "").trim();
    }

    public void setWebPort(int port) {
        properties.setProperty("web_port", String.valueOf(port));
        saveConfig();
    }

    public void setStartRunEnabled(boolean enabled) {
        properties.setProperty("enable_start_run", String.valueOf(enabled));
        saveConfig();
    }

    public void setMonitorWebStatus(String url) {
        properties.setProperty("monitor_web_status", url);
        saveConfig();
    }
}