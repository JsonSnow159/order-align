package com.example.orderalign.controller;

import com.example.orderalign.dto.OrderAlignDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;
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
        // This executor is for running the 4 methods in parallel inside the loop
        ExecutorService innerExecutor = Executors.newFixedThreadPool(4);

        while (isFixRunning.get()) {
            try {
                log.debug("Running data fix iteration with parallel steps...");

                OrderAlignDTO dto = new OrderAlignDTO();
                dto.setAppId(appId);
                dto.setRootKdtId(rootKdtId);

                CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> kaiLeShiOrderAlignController.queryOutDetail(dto), innerExecutor);
                CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> kaiLeShiOrderAlignController.queryTid(dto), innerExecutor);
                CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> kaiLeShiOrderAlignController.queryYzDetail(dto), innerExecutor);
//                CompletableFuture<Void> future4 = CompletableFuture.runAsync(() -> kaiLeShiOrderAlignController.detailAlign(dto), innerExecutor);

                // Wait for all 4 parallel tasks in this iteration to complete
//                CompletableFuture.allOf(future1, future2, future3, future4).join();
                CompletableFuture.allOf(future1, future2, future3).join();

                log.debug("Parallel data fix iteration completed.");

            } catch (Exception e) {
                log.error("Error during parallel data fix process, stopping loop.", e);
                isFixRunning.set(false); // Stop the loop on error
            }
        }

        // Shutdown the inner executor when the loop finishes
        shutdownInnerExecutor(innerExecutor);
        log.info("Continuous data fix loop has stopped.");
    }

    private void shutdownInnerExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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