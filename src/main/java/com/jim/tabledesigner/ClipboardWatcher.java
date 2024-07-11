package com.jim.tabledesigner;

import net.sf.jsqlparser.JSQLParserException;
import org.dom4j.DocumentException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.TimerTask;

public class ClipboardWatcher implements ClipboardOwner {

    private Clipboard systemClipboard;
    private ExecutorService executor;

    public ClipboardWatcher() {
        systemClipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        executor = Executors.newSingleThreadExecutor();
    }

    public void startMonitoring() {
        executor.submit(() -> {
            Transferable contents = systemClipboard.getContents(this);
            gainOwnership(contents);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(200); // Sleep for a small delay to check for clipboard changes
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Good practice to re-interrupt the thread
                }
            }
        });
    }

    private void gainOwnership(Transferable t) {
        systemClipboard.setContents(t, this);
    }


    public void lostOwnership(Clipboard clipboard, Transferable contents) {
        Timer timer = new Timer("ClipboardWatcherTimer");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    Transferable newContents = clipboard.getContents(this);
                    processContents(newContents);
                    gainOwnership(newContents);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 500); // 延迟 500 毫秒后尝试
    }

    private void processContents(Transferable newContents) {
        try {
            // 检查是否为字符串数据
            if (newContents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                String data = (String) newContents.getTransferData(DataFlavor.stringFlavor);
                if (data != null) {
                    updateFile(data);
                }
            }
            // 检查是否为图像数据
            else if (newContents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                BufferedImage image = (BufferedImage) newContents.getTransferData(DataFlavor.imageFlavor);
                if (image != null) {
                    processImage(image);
                }
            }
        } catch (UnsupportedFlavorException | IOException e) {
            System.err.println("访问剪贴板数据时出错: " + e.getMessage());
        }
    }

    /**
     * 将BufferedImage转换为Base64字符串
     * @param image 图像对象
     * @param format 图像格式（如 "png", "jpeg"）
     * @return Base64编码的字符串
     * @throws IOException
     */
    public static String encodeToString(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(image, format, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private void processImage(BufferedImage image) throws IOException {
        // 对图像数据进行base64编码并打印
        String base64 = encodeToString(image, "png"); ;
//        ApiCaller.postRequest(base64);
        // base64保存在当前工作目录下的image.txt文件中
        try (PrintWriter writer = new PrintWriter("image.txt")) {
            writer.println(base64);
        }
        LangChainCaller.executeScript();

        // 读取当前工作目录下的gpt-gen-sql.txt文件
        try (BufferedReader reader = new BufferedReader(new FileReader("gpt-gen-sql.txt"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            updateFile(sb.toString());
        }
    }


    private void updateFile(String data) {
        try {
            // 如果是SQL语句，尝试解析并更新addTable.sql文件
            if(Main.tryParseSQL(data)) {
                Files.write(Paths.get("addTable.sql"), data.getBytes());
                Main.processNewTable();
            }
//            System.out.println("addTable.sql文件已更新。");
        } catch (IOException e) {
            System.err.println("写入addTable.sql失败: " + e.getMessage());
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (JSQLParserException e) {
            e.printStackTrace();
        }
    }

}