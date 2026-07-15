package com.example.orderalign.controller;

import com.example.orderalign.dto.OrderAlignDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderalign.mapper.KaiLeShiOrderAlignMapper;
import com.example.orderalign.model.KaiLeShiOrderAlign;
import com.example.orderalign.mapper.KlsOrderMapper;
import com.example.orderalign.model.KlsOrder;
import org.apache.commons.collections.CollectionUtils;
import java.util.ArrayList;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/kaileshi/datafix")
public class KaiLeShiDataFixController {

    @Resource
    private KaiLeShiOrderAlignController kaiLeShiOrderAlignController;

    @Resource
    private KaiLeShiOrderAlignMapper kaiLeShiOrderAlignMapper;

    @Resource
    private KlsOrderMapper klsOrderMapper;

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
    private final Object fixLock = new Object();
    private boolean isFixTaskRunning = false;

    private final AtomicBoolean isMigrationRunning = new AtomicBoolean(false);
    private final ExecutorService migrationExecutor = Executors.newSingleThreadExecutor();
    private final Object migrationLock = new Object();
    private boolean isMigrationTaskRunning = false;
    private volatile long migrationProcessedCount = 0;
    private volatile long migrationLastId = 0;

    /**
     * Starts the continuous data fix process in the background.
     */
    @GetMapping("/start")
    public String startFix() {
        synchronized (fixLock) {
            while (isFixTaskRunning && !isFixRunning.get()) {
                try {
                    // Wait for the stopping thread to finish and notify
                    fixLock.wait(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Start interrupted while waiting for previous process to stop.";
                }
            }

            if (isFixRunning.get()) {
                String message = "Continuous data fix process is already running.";
                log.warn(message);
                return message;
            }

            isFixRunning.set(true);
            isFixTaskRunning = true;
            mainLoopExecutor.submit(this::runFixLoop);
            String message = "Continuous data fix process started (with parallel steps).";
            log.info(message);
            return message;
        }
    }

    /**
     * Stops the continuous data fix process gracefully.
     */
    @GetMapping("/stop")
    public String stopFix() {
        synchronized (fixLock) {
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
    }

    /**
     * Checks the status of the data fix process.
     */
    @GetMapping("/status")
    public String getStatus() {
        return "Data fix process is " + (isFixRunning.get() ? "running." : "not running.");
    }

    /**
     * Starts the data migration from kls_order (status=2) to kaileshi_order_align.
     */
    @GetMapping("/migrate/start")
    public String startMigration() {
        synchronized (migrationLock) {
            while (isMigrationTaskRunning && !isMigrationRunning.get()) {
                try {
                    migrationLock.wait(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "Start interrupted while waiting for previous migration to stop.";
                }
            }

            if (isMigrationRunning.get()) {
                String message = "Migration process is already running.";
                log.warn(message);
                return message;
            }

            isMigrationRunning.set(true);
            isMigrationTaskRunning = true;
            migrationExecutor.submit(this::runMigrationLoop);
            String message = "Migration process from kls_order to kaileshi_order_align started in the background.";
            log.info(message);
            return message;
        }
    }

    /**
     * Stops the migration process gracefully.
     */
    @GetMapping("/migrate/stop")
    public String stopMigration() {
        synchronized (migrationLock) {
            if (isMigrationRunning.compareAndSet(true, false)) {
                String message = "Stopping migration process. It will terminate after the current batch.";
                log.info(message);
                return message;
            } else {
                String message = "Migration process is not running.";
                log.warn(message);
                return message;
            }
        }
    }

    /**
     * Checks the status/progress of the migration process.
     */
    @GetMapping("/migrate/status")
    public String getMigrationStatus() {
        return String.format("Migration process is %s. Processed count: %d, Last processed ID: %d",
                isMigrationRunning.get() ? "running" : "not running",
                migrationProcessedCount,
                migrationLastId);
    }

    private void runMigrationLoop() {
        log.info("Starting kls_order status=2 migration loop from lastId: {}", migrationLastId);
        int batchSize = 1000;
        try {
            while (true) {
                synchronized (migrationLock) {
                    if (!isMigrationRunning.get()) {
                        break;
                    }
                }

                try {
                    List<KlsOrder> orders = klsOrderMapper.selectByStatusAndIdGreaterThan(2, migrationLastId, batchSize);
                    if (CollectionUtils.isEmpty(orders)) {
                        log.info("No more status=2 orders found in kls_order. Migration completed successfully.");
                        synchronized (migrationLock) {
                            isMigrationRunning.set(false);
                        }
                        break;
                    }

                    List<String> outTidList = new ArrayList<>();
                    long maxIdInBatch = migrationLastId;
                    for (KlsOrder order : orders) {
                        if (order.getOutTid() != null && !order.getOutTid().trim().isEmpty()) {
                            outTidList.add(order.getOutTid());
                        }
                        if (order.getId() > maxIdInBatch) {
                            maxIdInBatch = order.getId();
                        }
                    }

                    if (!outTidList.isEmpty()) {
                        OrderAlignDTO orderAlignDTO = new OrderAlignDTO();
                        orderAlignDTO.setAppId(appId != null ? appId : "42243307_kylin");
                        orderAlignDTO.setRootKdtId(rootKdtId != null ? rootKdtId : 42243307L);
                        orderAlignDTO.setOutTidList(outTidList);

                        log.info("Processing migration batch of {} orders, max ID: {}", outTidList.size(), maxIdInBatch);
                        kaiLeShiOrderAlignController.uploadOrder(orderAlignDTO);

                        migrationProcessedCount += outTidList.size();
                    }

                    migrationLastId = maxIdInBatch;

                    // Brief yield to not monopolize resources if running continuously
                    TimeUnit.MILLISECONDS.sleep(10);
                } catch (Exception e) {
                    log.error("Error occurred during kls_order migration. Migration paused.", e);
                    synchronized (migrationLock) {
                        isMigrationRunning.set(false);
                    }
                    break;
                }
            }
        } finally {
            synchronized (migrationLock) {
                isMigrationTaskRunning = false;
                isMigrationRunning.set(false);
                migrationLock.notifyAll();
            }
            log.info("Migration loop exited. Last processed ID: {}, Total processed: {}", migrationLastId, migrationProcessedCount);
        }
    }

    private void runFixLoop() {
        log.info("Starting continuous data fix loop...");
        int consecutiveErrors = 0;
        final int maxConsecutiveErrors = 5;

        try {
            while (true) {
                synchronized (fixLock) {
                    if (!isFixRunning.get()) {
                        break;
                    }
                }

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
                        synchronized (fixLock) {
                            isFixRunning.set(false);
                        }
                        break;
                    }

                    log.info("Running data fix iteration with sequential pipeline steps...");

                    OrderAlignDTO dto = new OrderAlignDTO();
                    dto.setAppId(appId);
                    dto.setRootKdtId(rootKdtId);

                    // Execute the pipeline steps sequentially - one status to completion at a time
                    processStatus(STATUS_PENDING, dto);
                    processStatus(STATUS_OUT_DETAIL_QUERIED, dto);
                    processStatus(STATUS_FOUND, dto);
                    processStatus(STATUS_DETAIL_QUERIED, dto);

                    log.info("Data fix iteration completed successfully.");
                    consecutiveErrors = 0; // Reset consecutive errors on successful execution

                } catch (Exception e) {
                    consecutiveErrors++;
                    log.error("Error during data fix process (consecutive errors: {}/{})", consecutiveErrors, maxConsecutiveErrors, e);
                    
                    if (consecutiveErrors >= maxConsecutiveErrors) {
                        log.error("Reached maximum consecutive errors limit. Stopping loop to prevent infinite retry loops.");
                        synchronized (fixLock) {
                            isFixRunning.set(false);
                        }
                        break;
                    }

                    try {
                        // Backoff sleep before retrying
                        TimeUnit.SECONDS.sleep(5);
                    } catch (InterruptedException ie) {
                        log.info("Data fix loop interrupted during error backoff sleep. Stopping loop.");
                        synchronized (fixLock) {
                            isFixRunning.set(false);
                        }
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            synchronized (fixLock) {
                isFixTaskRunning = false;
                isFixRunning.set(false);
                fixLock.notifyAll();
            }
            log.info("Continuous data fix loop has stopped.");
        }
    }

    private void processStatus(int status, OrderAlignDTO dto) {
        log.info("Start processing status: {}", status);
        int consecutiveNoProgress = 0;
        Set<Long> lastIds = Collections.emptySet();

        while (isFixRunning.get()) {
            List<KaiLeShiOrderAlign> currentList = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(status, 100);
            if (CollectionUtils.isEmpty(currentList)) {
                break;
            }

            Set<Long> currentIds = currentList.stream()
                    .map(KaiLeShiOrderAlign::getId)
                    .collect(Collectors.toSet());

            if (currentIds.equals(lastIds)) {
                consecutiveNoProgress++;
                if (consecutiveNoProgress >= 3) {
                    log.warn("No progress made for status {} after 3 attempts on the same items. Moving to next status.", status);
                    break;
                }
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } else {
                consecutiveNoProgress = 0;
            }
            lastIds = currentIds;

            // Invoke the corresponding step
            switch (status) {
                case STATUS_PENDING:
                    kaiLeShiOrderAlignController.queryOutDetail(dto);
                    break;
                case STATUS_OUT_DETAIL_QUERIED:
                    kaiLeShiOrderAlignController.queryTid(dto);
                    break;
                case STATUS_FOUND:
                    kaiLeShiOrderAlignController.queryYzDetail(dto);
                    break;
                case STATUS_DETAIL_QUERIED:
                    kaiLeShiOrderAlignController.detailAlign(dto);
                    break;
                default:
                    log.error("Unknown status: {}", status);
                    return;
            }
        }
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down data fix and migration executors due to application shutdown.");
        synchronized (fixLock) {
            isFixRunning.set(false);
            fixLock.notifyAll();
        }
        synchronized (migrationLock) {
            isMigrationRunning.set(false);
            migrationLock.notifyAll();
        }
        mainLoopExecutor.shutdown();
        migrationExecutor.shutdown();
        try {
            boolean terminated1 = mainLoopExecutor.awaitTermination(15, TimeUnit.SECONDS);
            boolean terminated2 = migrationExecutor.awaitTermination(15, TimeUnit.SECONDS);
            if (!terminated1) {
                mainLoopExecutor.shutdownNow();
            }
            if (!terminated2) {
                migrationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            mainLoopExecutor.shutdownNow();
            migrationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}