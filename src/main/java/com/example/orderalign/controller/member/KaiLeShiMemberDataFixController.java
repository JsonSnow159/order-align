package com.example.orderalign.controller.member;

import com.example.orderalign.dto.member.MemberAlignDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@RequestMapping("/kaileshi/member/datafix")
public class KaiLeShiMemberDataFixController {

    @Resource
    private KaiLeShiMemberAlignController kaiLeShiMemberAlignController;

    @Value("${kaileshi.align.appId}")
    private String appId;

    @Value("${kaileshi.align.rootKdtId}")
    private Long rootKdtId;

    private final AtomicBoolean isFixRunning = new AtomicBoolean(false);
    private volatile String currentStage = "";
    // This executor is for the main loop
    private final ExecutorService mainLoopExecutor = Executors.newSingleThreadExecutor();

    /**
     * Starts the continuous data fix process for a specific stage.
     * @param stage The stage to run. Valid stages are: "queryOutDetail", "detailAlign".
     */
    @GetMapping("/start")
    public String startFix(@RequestParam("stage") String stage) {
        if (!isValidStage(stage)) {
            return "Error: Invalid stage specified. Valid stages are: queryOutDetail, detailAlign.";
        }

        if (isFixRunning.compareAndSet(false, true)) {
            this.currentStage = stage;
            mainLoopExecutor.submit(this::runFixLoop);
            String message = "Continuous data fix process started for stage: " + stage;
            log.info(message);
            return message;
        } else {
            String message = "Error: Process is already running stage: " + this.currentStage;
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
            String message = "Stopping data fix process for stage '" + this.currentStage + "'. It will terminate after the current iteration.";
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
        if (isFixRunning.get()) {
            return "Data fix process is RUNNING. Current stage: " + this.currentStage;
        } else {
            return "Data fix process is NOT RUNNING.";
        }
    }

    private void runFixLoop() {
        log.info("Starting loop for stage: {}", this.currentStage);

        while (isFixRunning.get()) {
            try {
                MemberAlignDTO dto = new MemberAlignDTO();
                dto.setAppId(appId);
                dto.setRootKdtId(rootKdtId);

                log.info("Executing stage: {}", this.currentStage);
                switch (this.currentStage) {
                    case "queryOutDetail":
                        kaiLeShiMemberAlignController.queryOutDetail(dto);
                        break;
                    case "detailAlign":
                        kaiLeShiMemberAlignController.detailAlign(dto);
                        break;
                    default:
                        log.error("Unknown stage: {}. Stopping loop.", this.currentStage);
                        isFixRunning.set(false);
                        break;
                }

                if (!isFixRunning.get()) { // Check if stop was requested during execution
                    break;
                }

                log.info("Stage '{}' iteration complete. Pausing for 5 seconds.", this.currentStage);
                TimeUnit.SECONDS.sleep(5);

            } catch (InterruptedException e) {
                log.warn("Data fix loop for stage '{}' interrupted. Shutting down.", this.currentStage);
                isFixRunning.set(false); // Stop the loop
                Thread.currentThread().interrupt(); // Preserve the interrupted status
            } catch (Exception e) {
                log.error("Error during data fix process for stage '" + this.currentStage + "', stopping loop.", e);
                isFixRunning.set(false); // Stop the loop on other errors
            }
        }
        log.info("Loop for stage '{}' has stopped.", this.currentStage);
        this.currentStage = ""; // Reset stage
    }

    private boolean isValidStage(String stage) {
        return "queryOutDetail".equals(stage) || "detailAlign".equals(stage);
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
