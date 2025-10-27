package com.example.orderalign.utils;

import java.io.*;

public class SQLFileSplitter {

    public static void main(String[] args) {
        // =================================================================================
        // =========================== 请在这里修改为你自己的文件路径 ==========================
        // =================================================================================
        // sourceFile:  你的那个 2.1GB 的 SQL 源文件的完整路径
        String sourceFile = "/PATH/TO/YOUR/2.1GB_FILE.sql";
        // outputDir:   你希望分割后的小文件存放的目录的完整路径
        String outputDir = "/PATH/TO/YOUR/output_directory";
        // =================================================================================

        String fileNamePrefix = "chunk_";           // 分割后文件前缀
        String fileExtension = ".sql";              // 文件扩展名
        int linesPerFile = 200000;                   // 每个文件的行数

        // 调用增强后的分割方法
        splitSQLFileByLines(sourceFile, outputDir, fileNamePrefix, fileExtension, linesPerFile);
    }

    /**
     * 按行数分割SQL文件（增强版：带详细日志和异常捕获）
     *
     * @param sourceFilePath 源SQL文件路径
     * @param outputDirectory 输出目录
     * @param prefix 输出文件前缀
     * @param extension 输出文件扩展名
     * @param linesPerFile 每个文件包含的行数
     */
    public static void splitSQLFileByLines(String sourceFilePath, String outputDirectory,
                                          String prefix, String extension, int linesPerFile) {
        try {
            System.out.println("--- 开始执行分割逻辑 ---");
            System.out.println("源文件: " + sourceFilePath);
            System.out.println("输出目录: " + outputDirectory);

            File sourceFile = new File(sourceFilePath);
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                System.err.println("错误：源文件不存在或不是一个文件: " + sourceFilePath);
                return;
            }

            File outputDir = new File(outputDirectory);
            if (!outputDir.exists()) {
                System.out.println("输出目录不存在，正在创建: " + outputDir.getAbsolutePath());
                outputDir.mkdirs();
            }
            if (!outputDir.isDirectory() || !outputDir.canWrite()) {
                System.err.println("错误：输出目录不是一个有效的、可写的目录: " + outputDirectory);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sourceFilePath), "UTF-8"))) {
                String line;
                int fileCounter = 1;
                int lineCounter = 0;
                BufferedWriter writer = null;

                System.out.println("开始逐行读取源文件...");

                while ((line = reader.readLine()) != null) {
                    if (lineCounter % linesPerFile == 0) {
                        if (writer != null) {
                            writer.close();
                            System.out.println("已关闭文件: " + (prefix + (fileCounter - 1) + extension));
                        }
                        String outputFilePath = outputDirectory + File.separator + prefix + fileCounter + extension;
                        System.out.println("创建新文件: " + outputFilePath);
                        writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), "UTF-8"));
                        fileCounter++;
                    }

                    if (writer != null) {
                        writer.write(line);
                        writer.newLine();
                    }
                    lineCounter++;

                    if (lineCounter > 0 && lineCounter % 50000 == 0) { // 每处理5万行打印一次进度
                        System.out.println("...已处理 " + lineCounter + " 行...");
                    }
                }

                if (writer != null) {
                    writer.close();
                }

                System.out.println("--- 分割处理完成 ---");
                // 如果lineCounter为0，说明源文件为空，fileCounter会是1，(1-1)=0，正确
                // 如果lineCounter>0，fileCounter至少为2，(2-1)=1，正确
                System.out.println("总计生成 " + (fileCounter > 1 ? (fileCounter - 1) : 0) + " 个文件。");
                System.out.println("总计处理 " + lineCounter + " 行。");
            }
        } catch (IOException e) {
            System.err.println(" !!! 发生文件读写错误 !!!");
            e.printStackTrace();
        } catch (OutOfMemoryError oom) {
            System.err.println(" !!! 发生严重错误：内存不足 !!!");
            System.err.println("这通常意味着SQL文件包含一个或多个非常长的行。");
            System.err.println("此程序按行分割，可能不适用于单行过大的文件。");
            oom.printStackTrace();
        } catch (Exception e) {
            System.err.println(" !!! 发生未知异常 !!!");
            e.printStackTrace();
        }
    }
}
