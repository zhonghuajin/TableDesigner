package com.jim.tabledesigner;

import java.io.*;

public class LangChainCaller {
    public static File extractResource(String resourceName) throws Exception {
        InputStream resourceStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (resourceStream == null) {
            throw new IllegalArgumentException("resource " + resourceName + " not found");
        }

        File tempFile = File.createTempFile("script", ".py");
        tempFile.deleteOnExit();

        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = resourceStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        return tempFile;
    }


    // 执行 Python 脚本
    public static  void executeScript() {
        // ANSI红色开始标记
        final String ANSI_RED = "\u001B[31m";
        // ANSI重置标记
        final String ANSI_RESET = "\u001B[0m";
        System.out.println(ANSI_RED + " 注意：需要先配置命令行的代理服务器，否则无法进行分析 " + ANSI_RESET);
        StringBuilder output = new StringBuilder();
        try {
            File pythonScript = extractResource("gpt-4o.py");
            String[] command = new String[2];
            command[0] = "python";
            command[1] = pythonScript.getAbsolutePath();

            ProcessBuilder builder = new ProcessBuilder(command);
            Process process = builder.start();

            // 启动一个线程来显示正在分析图片
            Thread messageThread = new Thread(() -> {
                try {
                    while (process.isAlive()) {
                        System.out.println("正在分析图片...");
                        Thread.sleep(5000);  // 每隔1秒打印一次
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            messageThread.start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Script execution failed with exit code " + exitCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute script", e);
        }

        // 提示图片分析完成
        System.out.println(ANSI_RED + "图片分析完成！" + ANSI_RESET);

    }
}
