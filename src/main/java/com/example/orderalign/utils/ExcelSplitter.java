package com.example.orderalign.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ExcelSplitter {
    // 每个文件的最大行数
    private static final int MAX_ROWS_PER_FILE = 100000;

    /**
     * 使用事件模型拆分超大Excel文件，解决内存不足问题
     */
    public static void splitLargeExcelFile(String inputFilePath, String outputDirectory, String baseFileName)
            throws Exception {
        log.info("开始处理超大Excel文件拆分...");
        log.info("输入文件: " + inputFilePath);
        log.info("输出目录: " + outputDirectory);
        log.info("每个文件最大行数: " + MAX_ROWS_PER_FILE);

        // 确保输出目录存在
        Path outputPath = Paths.get(outputDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
            log.info("已创建输出目录: " + outputDirectory);
        }

        // 获取文件大小信息
        File inputFile = new File(inputFilePath);
        long fileSize = inputFile.length();
        double fileSizeMB = fileSize / (1024.0 * 1024.0);
        log.info(String.format("文件大小: %.2f MB", fileSizeMB));

        // 打开Excel文件
        log.info("正在打开Excel文件...");
        try (OPCPackage pkg = OPCPackage.open(inputFile, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable sharedStringsTable = new ReadOnlySharedStringsTable(pkg);
            StylesTable stylesTable = reader.getStylesTable();

            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            int sheetIndex = 0;

            while (sheetIterator.hasNext()) {
                InputStream sheetStream = sheetIterator.next();
                sheetIndex++;
                String sheetName = sheetIterator.getSheetName();
                log.info("开始处理工作表 " + sheetIndex + ": '" + sheetName + "'");
                
                // 创建数据处理器 - 自动处理表头和数据
                DataSplitter dataSplitter = new DataSplitter(
                        outputDirectory,
                        baseFileName,
                        sheetName
                );
                
                // 创建SAX解析器进行处理
                XMLReader xmlReader = SAXHelper.newXMLReader();
                ContentHandler handler = new XSSFSheetXMLHandler(
                        stylesTable,
                        sharedStringsTable,
                        dataSplitter,
                        false
                );
                xmlReader.setContentHandler(handler);
                
                // 解析数据
                log.info("开始解析工作表数据...");
                xmlReader.parse(new InputSource(sheetStream));
                sheetStream.close();
                
                // 确保最后一个文件被保存
                dataSplitter.completeProcessing();
                
                log.info("工作表 '" + sheetName + "' 处理完成，共创建 " + dataSplitter.getFileCount() + " 个文件");
            }
        }

        log.info("Excel文件拆分完成");
    }

    /**
     * 数据拆分器 - 处理表头和数据，每个文件添加表头
     */
    private static class DataSplitter implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final String outputDirectory;
        private final String baseFileName;
        private final String sheetName;
        private final Map<Integer, String> headers = new LinkedHashMap<>();
        
        private int currentRow = -1;
        private int fileIndex = 0;
        private SXSSFWorkbook currentWorkbook;
        private Sheet currentSheet;
        private int rowsInCurrentFile = 0;
        private int totalRows = 0;
        private boolean headerCaptured = false;
        
        public DataSplitter(String outputDirectory, String baseFileName, String sheetName) {
            this.outputDirectory = outputDirectory;
            this.baseFileName = baseFileName;
            this.sheetName = sheetName;
            
            // 创建第一个工作簿
            createNewWorkbook();
        }
        
        private void createNewWorkbook() {
            // 保存上一个工作簿
            if (currentWorkbook != null) {
                saveWorkbook();
            }
            
            // 创建新的工作簿
            currentWorkbook = new SXSSFWorkbook(100);
            currentWorkbook.setCompressTempFiles(true);
            currentSheet = currentWorkbook.createSheet(sheetName);
            rowsInCurrentFile = 0;
            fileIndex++;
            
            log.info("创建新的输出文件 " + fileIndex);
            
            // 如果已经收集了表头，则写入表头
            if (headerCaptured && !headers.isEmpty()) {
                Row headerRow = currentSheet.createRow(0);
                for (Map.Entry<Integer, String> entry : headers.entrySet()) {
                    Cell cell = headerRow.createCell(entry.getKey());
                    cell.setCellValue(entry.getValue());
                }
                rowsInCurrentFile = 1; // 表头占用一行
                log.info("已添加表头到文件 " + fileIndex + "，共 " + headers.size() + " 列");
            }
        }
        
        private void saveWorkbook() {
            if (currentWorkbook != null && rowsInCurrentFile > 0) {
                String outputFilePath = String.format("%s/%s_%s_part%d.xlsx",
                        outputDirectory, baseFileName, sheetName, fileIndex);
                
                log.info("保存文件: " + outputFilePath + " (包含 " + rowsInCurrentFile + " 行)");
                
                try (FileOutputStream out = new FileOutputStream(outputFilePath)) {
                    currentWorkbook.write(out);
                    currentWorkbook.dispose(); // 释放临时文件
                } catch (IOException e) {
                    log.info("保存文件时出错: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            
            // 处理表头行
            if (rowNum == 0) {
                // 第一次遇到第0行，标记为表头行
                // 表头会在单元格处理时收集
                return;
            }
            
            // 检查是否需要创建新的工作簿（达到最大行数）
            if (rowsInCurrentFile >= MAX_ROWS_PER_FILE) {
                log.info(String.format("已达到每个文件的最大行数 (%d)，创建新文件", MAX_ROWS_PER_FILE));
                createNewWorkbook();
            }
            
            // 为数据行创建行
            int targetRowIndex;
            if (headerCaptured && !headers.isEmpty()) {
                // 如果有表头，数据行从索引1开始
                targetRowIndex = rowsInCurrentFile;
            } else {
                // 没有表头，数据行从索引0开始
                targetRowIndex = rowsInCurrentFile;
            }
            
            currentSheet.createRow(targetRowIndex);
        }
        
        @Override
        public void endRow(int rowNum) {
            // 如果是表头行
            if (rowNum == 0) {
                headerCaptured = true;
                log.info("表头处理完成，收集到 " + headers.size() + " 列");
                
                // 第一个文件的表头行已在单元格处理阶段创建
                if (fileIndex == 1 && !headers.isEmpty()) {
                    rowsInCurrentFile = 1; // 表头占用第一行
                }
                return;
            }
            
            // 数据行，递增计数器
            rowsInCurrentFile++;
            totalRows++;
            
            // 每1000行输出一次进度
            if (totalRows % 1000 == 0) {
                log.info(String.format("已处理 %d 行，当前文件: %d", totalRows, fileIndex));
            }
        }
        
        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (formattedValue == null) {
                return; // 跳过空值
            }
            
            // 解析单元格引用获取列索引
            CellReference ref = new CellReference(cellReference);
            int col = ref.getCol();
            
            // 处理表头行单元格
            if (currentRow == 0) {
                headers.put(col, formattedValue);
                
                // 在第一个文件中直接创建表头单元格
                Row headerRow = currentSheet.getRow(0);
                if (headerRow == null) {
                    headerRow = currentSheet.createRow(0);
                }
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(formattedValue);
                return;
            }
            
            // 处理数据行单元格
            int rowIndex;
            if (headerCaptured && !headers.isEmpty()) {
                // 有表头的情况下，数据行从索引1开始
                rowIndex = rowsInCurrentFile;
            } else {
                // 无表头的情况下，数据行从索引0开始
                rowIndex = rowsInCurrentFile;
            }
            
            // 获取数据行
            Row dataRow = currentSheet.getRow(rowIndex);
            if (dataRow == null) {
                log.info("警告: 找不到行 " + rowIndex + "，源行=" + currentRow);
                return;
            }
            
            // 创建数据单元格
            Cell cell = dataRow.createCell(col);
            cell.setCellValue(formattedValue);
        }
        
        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // 不处理页眉页脚
        }
        
        /**
         * 完成处理并保存最后一个工作簿
         */
        public void completeProcessing() {
            saveWorkbook();
        }
        
        /**
         * 获取已创建的文件数量
         */
        public int getFileCount() {
            return fileIndex;
        }
    }

    public static void main(String[] args) {
        try {
            long startTime = System.currentTimeMillis();

            String inputFilePath = "/Users/app/Downloads/主退单的副本.xls";
            String outputDirectory = "/Users/app/Downloads/凯乐石相关/全量退单";

            // 获取不带扩展名的文件名
            Path inputPath = Paths.get(inputFilePath);
            String fileName = inputPath.getFileName().toString();
            String baseName = fileName;
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
            }

            log.info("开始Excel文件拆分程序 (低内存模式)");
            // 为JVM添加以下参数可以提高性能: -Xmx2g -XX:+UseG1GC
            splitLargeExcelFile(inputFilePath, outputDirectory, baseName);

            long endTime = System.currentTimeMillis();
            double totalTimeSeconds = (endTime - startTime) / 1000.0;
            log.info(String.format("程序执行完成，总耗时: %.2f 秒", totalTimeSeconds));
        } catch (Exception e) {
            log.info("程序执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

