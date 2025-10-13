package com.example.orderalign.controller;

import com.alibaba.fastjson.JSON;
import com.example.orderalign.dto.YzOrderDetail;
import com.example.orderalign.mapper.YouzanOrderDetailMapper;
import com.example.orderalign.model.YouzanOrderDetail;
import com.youzan.cloud.connector.sdk.client.YzCloudResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


@Slf4j
@RestController
@RequestMapping("/kaileshi/noSourceRefund")
public class KaiLeShiNoSourceRefundOrderUploadController {

    private final YouzanOrderDetailMapper youzanOrderDetailMapper;

    public KaiLeShiNoSourceRefundOrderUploadController(YouzanOrderDetailMapper youzanOrderDetailMapper) {
        this.youzanOrderDetailMapper = youzanOrderDetailMapper;
    }

    @PostMapping("/uploadExcel")
    public YzCloudResponse<Object> uploadExcel(@RequestParam("file") MultipartFile file) {
        // 调高POI内部记录大小限制，以处理大型Excel文件。此限制针对文件内部的单个数据记录，而非整个文件大小。
        org.apache.poi.util.IOUtils.setByteArrayMaxOverride(200 * 1024 * 1024);
        if (file.isEmpty()) {
            return YzCloudResponse.error(400, "文件不能为空");
        }

        log.info("开始处理上传的Excel文件: {}", file.getOriginalFilename());
        int processedOrderCount = 0;

        try (InputStream inputStream = file.getInputStream()) {
            log.info("使用XSSFWorkbook加载Excel文件到内存...");
            Workbook workbook = new XSSFWorkbook(inputStream);
            log.info("Excel文件加载完成.");
            Sheet sheet = workbook.getSheetAt(0);

            // 获取表头
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerMap = new HashMap<>();
            for (Cell cell : headerRow) {
                headerMap.put(cell.getStringCellValue(), cell.getColumnIndex());
            }

            int lastRowNum = sheet.getLastRowNum();
            log.info("开始遍历数据行，总行数: {}", lastRowNum);

            YzOrderDetail currentOrderDetail = null;
            String currentOutOrderNo = null;

            // 遍历数据行
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    log.warn("第 {} 行为空，跳过", i + 1);
                    continue;
                }

                if (i % 1000 == 0) {
                    log.info("已处理 {}/{} 行", i, lastRowNum);
                }

                String outOrderNo = translateToString(row.getCell(headerMap.get("out_order_no")));
                if (outOrderNo == null || outOrderNo.trim().isEmpty()) {
                    log.debug("第 {} 行 'out_order_no' 为空，跳过", i + 1);
                    continue; // 跳过没有订单号的行
                }
                Long kdtId = Long.parseLong(translateToString(row.getCell(headerMap.get("kdt_id"))));

                // 当订单号变化时，处理上一个订单
                if (currentOutOrderNo != null && !currentOutOrderNo.equals(outOrderNo)) {
                    // 在这里可以添加将 currentOrderDetail 保存到数据库的逻辑
                    YouzanOrderDetail record = new YouzanOrderDetail();
                    record.setTid(currentOrderDetail.getTid());
                    record.setKdtId(42243307L);
                    record.setAppId("42243307_kylin");
                    record.setStatus(1);
                    record.setTidDetail(JSON.toJSONString(currentOrderDetail));
                    youzanOrderDetailMapper.insert(record);
                    log.info("处理完成订单: {}, 包含 {} 个明细", currentOrderDetail.getTid(), currentOrderDetail.getOidList().size());
                    processedOrderCount++;
                    currentOrderDetail = null;
                }

                // 如果是新订单，创建对象
                if (currentOrderDetail == null) {
                    currentOutOrderNo = outOrderNo;
                    currentOrderDetail = new YzOrderDetail();
                    currentOrderDetail.setKdtId(kdtId);
                    currentOrderDetail.setTid(currentOutOrderNo);
                    currentOrderDetail.setUserId(translateToString(row.getCell(headerMap.get("user_id"))));
                    currentOrderDetail.setOidList(new ArrayList<>());
                    currentOrderDetail.setTotalPayAmount(0L);
                }

                // 添加子订单明细
                YzOrderDetail.SubOrder subOrder = new YzOrderDetail.SubOrder();
                String itemNo = translateToString(row.getCell(headerMap.get("item_no")));
                if (itemNo != null && itemNo.endsWith("_forAnalysis")) {
                    itemNo = itemNo.replace("_forAnalysis", "");
                }
                subOrder.setItemNo(itemNo);

                String skuNo = translateToString(row.getCell(headerMap.get("sku_no")));
                if (skuNo != null && skuNo.endsWith("_forAnalysis")) {
                    skuNo = skuNo.replace("_forAnalysis", "");
                }
                subOrder.setSkuNo(skuNo);

                subOrder.setOutOid(translateToString(row.getCell(headerMap.get("out_order_item_id"))));
                subOrder.setItemId(Long.parseLong(translateToString(row.getCell(headerMap.get("item_id")))));
                subOrder.setSkuId(Long.parseLong(translateToString(row.getCell(headerMap.get("sku_id")))));
                subOrder.setTitle(translateToString(row.getCell(headerMap.get("title"))));
                subOrder.setNum(Integer.parseInt(translateToString(row.getCell(headerMap.get("num")))));

                long payment = Long.parseLong(translateToString(row.getCell(headerMap.get("current_total_price"))));
                subOrder.setPayment(payment);

                currentOrderDetail.getOidList().add(subOrder);
                currentOrderDetail.setTotalPayAmount(currentOrderDetail.getTotalPayAmount() + payment);
            }

            // 处理文件中的最后一个订单
            if (currentOrderDetail != null) {
                // 在这里可以添加将 currentOrderDetail 保存到数据库的逻辑
                YouzanOrderDetail record = new YouzanOrderDetail();
                record.setTid(currentOrderDetail.getTid());
                record.setKdtId(42243307L);
                record.setAppId("42243307_kylin");
                record.setStatus(1);
                record.setTidDetail(JSON.toJSONString(currentOrderDetail));
                youzanOrderDetailMapper.insert(record);
                log.info("处理完成订单: {}, 包含 {} 个明细", currentOrderDetail.getTid(), currentOrderDetail.getOidList().size());
                processedOrderCount++;
            }

            log.info("所有行处理完毕，共处理 {} 笔订单", processedOrderCount);

            return YzCloudResponse.success("文件上传成功，共处理 " + processedOrderCount + " 笔订单。");

        } catch (Exception e) {
            log.error("解析Excel文件失败", e);
            return YzCloudResponse.error(500, "文件处理失败: " + e.getMessage());
        }
    }

    public static String translateToString(Cell cell) {
        if(Objects.isNull(cell)) {
            return "";
        }
        String cellValue = "";
        switch (cell.getCellTypeEnum()) {
            case NUMERIC:
                double numericCellValue = cell.getNumericCellValue();
                DecimalFormat df = new DecimalFormat("#");
                cellValue = df.format(numericCellValue);
                break;
            case STRING:
                cellValue = cell.getStringCellValue();
                break;
            case BOOLEAN:
                cellValue = cell.getBooleanCellValue() + "";
                break;
            case BLANK:
                break;
            case FORMULA:
                cellValue = cell.getCellFormula() + " ";
                break;
        }
        return cellValue;
    }
}
