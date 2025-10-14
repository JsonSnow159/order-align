package com.example.orderalign.utils;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.example.orderalign.dto.OrderAlignDTO;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Excel2DB {

    private static final String UPLOAD_URL = "http://localhost:8091/kls/upload";
    private static final RestTemplate restTemplate = new RestTemplate();
    private static final int BATCH_SIZE = 500;

    /**
     * Main method to run the Excel import process.
     * @param args Command line arguments. Expects one argument: the path to the directory containing Excel files.
     */
    public static void main(String[] args) {
        String directoryPath = "";
        System.out.println("Starting to process files from: " + directoryPath);
        readFromExcelAndUpload(directoryPath);
        System.out.println("Finished processing all files.");
    }

    public static void readFromExcelAndUpload(String directoryPath) {
        File folder = new File(directoryPath);
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".xlsx") || name.toLowerCase().endsWith(".xls"));

        if (files == null || files.length == 0) {
            System.out.println("The specified path is not a directory or contains no Excel files.");
            return;
        }

        List<String> outTidBatch = new ArrayList<>();

        for (File file : files) {
            System.out.println("Reading file: " + file.getName());
            EasyExcel.read(file, new ReadListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                    // Assuming the out_tid is in the first column (index 0)
                    String outTid = data.get(0);
                    if (outTid != null && !outTid.trim().isEmpty()) {
                        outTidBatch.add(outTid);
                        if (outTidBatch.size() == BATCH_SIZE) {
                            uploadBatch(new ArrayList<>(outTidBatch));
                            outTidBatch.clear();
                        }
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // This is called after each file is read.
                    // The final batch upload is handled after the loop.
                }
            }).sheet().doRead();
        }

        // Upload any remaining records
        if (!outTidBatch.isEmpty()) {
            uploadBatch(outTidBatch);
        }
    }

    private static void uploadBatch(List<String> outTidList) {
        OrderAlignDTO orderAlignDTO = new OrderAlignDTO();
        orderAlignDTO.setOutTidList(outTidList);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<OrderAlignDTO> request = new HttpEntity<>(orderAlignDTO, headers);

        try {
            System.out.println("Uploading batch of " + outTidList.size() + " orders...");
            String response = restTemplate.postForObject(UPLOAD_URL, request, String.class);
            System.out.println("Successfully uploaded batch. Response: " + response);
        } catch (Exception e) {
            System.err.println("Failed to upload batch of " + outTidList.size() + ".");
            e.printStackTrace();
        }
    }
}
