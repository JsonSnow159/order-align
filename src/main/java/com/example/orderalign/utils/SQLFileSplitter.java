package com.example.orderalign.utils;

import java.io.*;

public class SQLFileSplitter {

    public static void main(String[] args) {
        String sourceFile = "/Users/app/Downloads/kylin.sql";
        String outputDir = "/Users/app/Downloads/凯乐石相关/全量子订单";
        String fileNamePrefix = "chunk_";           // 分割后文件前缀
        String fileExtension = ".sql";              // 文件扩展名
        int linesPerFile = 200000;                   // 每个文件的行数

        try {
            splitSQLFileByLines(sourceFile, outputDir, fileNamePrefix, fileExtension, linesPerFile);
            System.out.println("SQL文件分割完成！");
        } catch (IOException e) {
            System.err.println("分割过程中出现错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 按行数分割SQL文件
     * 
     * @param sourceFilePath 源SQL文件路径
     * @param outputDirectory 输出目录
     * @param prefix 输出文件前缀
     * @param extension 输出文件扩展名
     * @param linesPerFile 每个文件包含的行数
     * @throws IOException 当文件读写出现问题时抛出
     */
    public static void splitSQLFileByLines(String sourceFilePath, String outputDirectory, 
                                          String prefix, String extension, int linesPerFile) throws IOException {
        // 确保输出目录存在
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFilePath), "UTF-8"))) {
            String line;
            int fileCounter = 1;    // 文件计数器
            int lineCounter = 0;    // 行计数器
            BufferedWriter writer = null;

            while ((line = reader.readLine()) != null) {
                // 每达到指定行数或writer为null时，创建新文件
                if (lineCounter % linesPerFile == 0) {
                    if (writer != null) {
                        writer.close();
                    }
                    String outputFilePath = outputDirectory + File.separator + prefix + fileCounter + extension;
                    writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), "UTF-8"));
                    fileCounter++;
                }

                if (writer != null) {
                    writer.write(line);
                    writer.newLine();
                }
                lineCounter++;
            }

            // 关闭最后一个文件的写入流
            if (writer != null) {
                writer.close();
            }
            
            System.out.println("分割完成。共生成 " + (fileCounter - 1) + " 个文件，总计处理 " + lineCounter + " 行。");
        }
    }
}