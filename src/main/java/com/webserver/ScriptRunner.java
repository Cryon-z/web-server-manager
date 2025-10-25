package com.webserver;

import java.io.*;

public class ScriptRunner {
    private Process process;
    private Thread outputReader;
    private boolean isRunning = false;

    public boolean start() {
        if (isRunning) {
            System.out.println("ℹ 脚本已在运行中");
            return true;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String scriptName = os.contains("win") ? "run.bat" : "run.sh";

        File scriptFile = new File(scriptName);
        if (!scriptFile.exists()) {
            System.out.println("ℹ 提示: 未找到脚本文件 " + scriptName);
            return false;
        }

        try {
            ProcessBuilder processBuilder;
            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd", "/c", scriptName);
            } else {
                Runtime.getRuntime().exec("chmod +x " + scriptName);
                processBuilder = new ProcessBuilder("sh", scriptName);
            }

            processBuilder.directory(new File("."));
            processBuilder.redirectErrorStream(true);

            process = processBuilder.start();
            isRunning = true;

            outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[脚本] " + line);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        System.err.println("✗ 读取脚本输出时出错: " + e.getMessage());
                    }
                }

                isRunning = false;
                try {
                    int exitCode = process.waitFor();
                    System.out.println("✓ 脚本执行完成，退出码: " + exitCode);
                } catch (InterruptedException e) {
                    System.err.println("✗ 等待脚本完成时被中断");
                    Thread.currentThread().interrupt();
                }
            });
            outputReader.start();

            System.out.println("✓ 脚本已启动: " + scriptName);
            return true;

        } catch (IOException e) {
            System.err.println("✗ 启动脚本失败: " + e.getMessage());
            return false;
        }
    }

    public void stop() {
        if (process != null && isRunning) {
            isRunning = false;
            process.destroy();
            try {
                if (outputReader != null && outputReader.isAlive()) {
                    outputReader.join(3000);
                }
            } catch (InterruptedException e) {
                System.err.println("✗ 停止脚本时被中断");
                Thread.currentThread().interrupt();
            }
            System.out.println("✓ 脚本已停止");
        }
    }

    public void restart() {
        System.out.println("🔄 正在重启脚本...");
        stop();
        start();
    }

    public boolean isRunning() {
        return isRunning;
    }
}