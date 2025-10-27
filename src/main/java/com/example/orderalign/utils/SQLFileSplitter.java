package com.example.orderalign.utils;

import java.io.*;

public class SQLFileSplitter {

    public static void main(String[] args) {
        // =================================================================================
        // =========================== 请在这里修改为你自己的文件路径 ==========================
        // =================================================================================
        // sourceFile:  你的 SQL 源文件的完整路径
        String sourceFile = "/Users/jincaiwu/Downloads/kylin.sql";
        // outputDir:   你希望分割后的小文件存放的目录的完整路径
        String outputDir = "/Users/jincaiwu/Downloads/凯乐石相关/全量子订单";
        // =================================================================================

        String fileNamePrefix = "insert_chunk_";
        String fileExtension = ".sql";
        // 每个分割文件包含的 INSERT 语句数量
        int insertsPerFile = 100;

        extractAndSplitInsertStatements(sourceFile, outputDir, fileNamePrefix, fileExtension, insertsPerFile);
    }

    /**
     * 提取SQL文件中的INSERT语句并按指定数量分割成多个文件。
     *
     * @param sourceFilePath  源SQL文件路径
     * @param outputDirectory 输出目录
     * @param prefix          输出文件前缀
     * @param extension       输出文件扩展名
     * @param insertsPerFile  每个文件包含的INSERT语句数
     */
    public static void extractAndSplitInsertStatements(String sourceFilePath, String outputDirectory,
                                                       String prefix, String extension, int insertsPerFile) {
        System.out.println("--- 开始执行 INSERT 语句提取与分割 --- ");
        System.out.println("源文件: " + sourceFilePath);
        System.out.println("输出目录: " + outputDirectory);
        System.out.println("每个文件包含 " + insertsPerFile + " 条 INSERT 语句。");

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
            int totalInsertsFound = 0;
            int fileCounter = 1;
            int insertsInCurrentFile = 0;

            BufferedWriter writer = null;
            StringBuilder statementBuffer = new StringBuilder();
            boolean inInsertStatement = false;

            System.out.println("开始扫描文件以查找 INSERT 语句...");

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                if (!inInsertStatement && trimmedLine.toLowerCase().startsWith("insert into")) {
                    inInsertStatement = true;
                }

                if (inInsertStatement) {
                    statementBuffer.append(line).append(System.lineSeparator());

                    if (trimmedLine.endsWith(";")) {
                        // 找到了一个完整的 INSERT 语句
                        totalInsertsFound++;

                        if (writer == null || insertsInCurrentFile >= insertsPerFile) {
                            if (writer != null) {
                                writer.close();
                                System.out.println("已关闭文件: " + (prefix + (fileCounter - 1) + extension));
                            }
                            String outputFilePath = outputDirectory + File.separator + prefix + fileCounter + extension;
                            System.out.println("创建新文件: " + outputFilePath);
                            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath), "UTF-8"));
                            fileCounter++;
                            insertsInCurrentFile = 0;
                        }

                        writer.write(statementBuffer.toString());
                        insertsInCurrentFile++;

                        // 重置状态以便寻找下一个INSERT语句
                        statementBuffer.setLength(0);
                        inInsertStatement = false;

                        if (totalInsertsFound > 0 && totalInsertsFound % 5000 == 0) {
                            System.out.println("...已找到并处理 " + totalInsertsFound + " 条 INSERT 语句...");
                        }
                    }
                }
                // 如果不是 inInsertStatement，则忽略该行
            }

            if (writer != null) {
                writer.close();
            }

            System.out.println(" --- 处理完成 ---");
            System.out.println("总计找到并写入 " + totalInsertsFound + " 条 INSERT 语句。");
            System.out.println("总计生成 " + (fileCounter > 1 ? (fileCounter - 1) : 0) + " 个文件。");

        } catch (IOException e) {
            System.err.println(" !!! 发生文件读写错误 !!!");
            e.printStackTrace();
        } catch (OutOfMemoryError oom) {
            System.err.println(" !!! 发生严重错误：内存不足 !!!");
            System.err.println("这通常意味着SQL文件包含一个或多个非常长的行。");
            oom.printStackTrace();
        } catch (Exception e) {
            System.err.println(" !!! 发生未知异常 !!!");
            e.printStackTrace();
        }
    }
}