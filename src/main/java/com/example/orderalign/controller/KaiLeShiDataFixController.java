package com.example.orderalign.controller;

import com.example.orderalign.dto.OrderAlignDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderalign.mapper.KaiLeShiOrderAlignMapper;
import com.example.orderalign.model.KaiLeShiOrderAlign;
import org.apache.commons.collections.CollectionUtils;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/kaileshi/datafix")
public class KaiLeShiDataFixController {

    @Resource
    private KaiLeShiOrderAlignController kaiLeShiOrderAlignController;

    @Resource
    private KaiLeShiOrderAlignMapper kaiLeShiOrderAlignMapper;

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_FOUND = 1;
    private static final int STATUS_DETAIL_QUERIED = 3;
    private static final int STATUS_OUT_DETAIL_QUERIED = 5;

    @Value("${kaileshi.align.appId}")
    private String appId;

    @Value("${kaileshi.align.rootKdtId}")
    private Long rootKdtId;

    private final AtomicBoolean isFixRunning = new AtomicBoolean(false);
    // This executor is for the main loop
    private final ExecutorService mainLoopExecutor = Executors.newSingleThreadExecutor();

    /**
     * Starts the continuous data fix process in the background.
     */
    @GetMapping("/start")
    public String startFix() {
        if (isFixRunning.compareAndSet(false, true)) {
            mainLoopExecutor.submit(this::runFixLoop);
            String message = "Continuous data fix process started (with parallel steps).";
            log.info(message);
            return message;
        } else {
            String message = "Continuous data fix process is already running.";
            log.warn(message);
            return message;
        }
    }

    /**
     * Stops the continuous data fix process gracefully.
     */
    @GetMapping("/stop")
    public String stopFix() {
        if (isFixRunning.compareAndSet(true, false)) {
            String message = "Stopping data fix process. It will terminate after the current iteration.";
            log.info(message);
            return message;
        } else {
            String message = "Data fix process is not running.";
            log.warn(message);
            return message;
        }
    }

    /**
     * Checks the status of the data fix process.
     */
    @GetMapping("/status")
    public String getStatus() {
        return "Data fix process is " + (isFixRunning.get() ? "running." : "not running.");
    }

    private void runFixLoop() {
        log.info("Starting continuous data fix loop...");
        int consecutiveErrors = 0;
        final int maxConsecutiveErrors = 5;

        while (isFixRunning.get()) {
            try {
                // Check if there is any data to process in the pipeline
                boolean hasData = false;
                List<Integer> activeStatuses = Arrays.asList(STATUS_PENDING, STATUS_OUT_DETAIL_QUERIED, STATUS_FOUND, STATUS_DETAIL_QUERIED);
                for (int status : activeStatuses) {
                    List<KaiLeShiOrderAlign> list = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(status, 1);
                    if (CollectionUtils.isNotEmpty(list)) {
                        hasData = true;
                        break;
                    }
                }

                if (!hasData) {
                    log.info("No more pending data found in the pipeline. Exiting data fix loop.");
                    isFixRunning.set(false);
                    break;
                }

                log.info("Running data fix iteration with sequential pipeline steps...");

                OrderAlignDTO dto = new OrderAlignDTO();
                dto.setAppId(appId);
                dto.setRootKdtId(rootKdtId);

                // Execute the pipeline steps sequentially
                kaiLeShiOrderAlignController.queryOutDetail(dto);
                kaiLeShiOrderAlignController.queryTid(dto);
                kaiLeShiOrderAlignController.queryYzDetail(dto);
                kaiLeShiOrderAlignController.detailAlign(dto);

                log.info("Data fix iteration completed successfully.");
                consecutiveErrors = 0; // Reset consecutive errors on successful execution

            } catch (Exception e) {
                consecutiveErrors++;
                log.error("Error during data fix process (consecutive errors: {}/{})", consecutiveErrors, maxConsecutiveErrors, e);
                
                if (consecutiveErrors >= maxConsecutiveErrors) {
                    log.error("Reached maximum consecutive errors limit. Stopping loop to prevent infinite retry loops.");
                    isFixRunning.set(false);
                    break;
                }

                try {
                    // Backoff sleep before retrying
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    log.info("Data fix loop interrupted during error backoff sleep. Stopping loop.");
                    isFixRunning.set(false);
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.info("Continuous data fix loop has stopped.");
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down data fix executor due to application shutdown.");
        isFixRunning.set(false);
        mainLoopExecutor.shutdown();
        try {
            if (!mainLoopExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                mainLoopExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            mainLoopExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}