package com.example.orderalign.controller;

import com.example.orderalign.dto.OrderAlignDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class KaiLeShiTspController {

    @Resource
    private KaiLeShiOrderAlignController kaiLeShiOrderAlignController;

    @Value("${kaileshi.align.appId}")
    private String appId;

    @Value("${kaileshi.align.rootKdtId}")
    private Long rootKdtId;

//    /**
//     * 定时任务，查询三方订单详情
//     */
//    @Scheduled(cron = "0/20 * * * * ?")
//    public void scheduledQueryOutDetail() {
//        log.info("【定时任务】开始执行【查询三方订单详情】...");
//        try {
//            OrderAlignDTO dto = new OrderAlignDTO();
//            dto.setAppId(appId);
//            dto.setRootKdtId(rootKdtId);
//            kaiLeShiOrderAlignController.queryOutDetail(dto);
//            log.info("【定时任务】【查询三方订单详情】执行成功");
//        } catch (Exception e) {
//            log.error("【定时任务】【查询三方订单详情】执行失败", e);
//        }
//    }
//
//    /**
//     * 定时任务，查询有赞Tid
//     */
//    @Scheduled(cron = "5/20 * * * * ?")
//    public void scheduledQueryTid() {
//        log.info("【定时任务】开始执行【查询有赞Tid】...");
//        try {
//            OrderAlignDTO dto = new OrderAlignDTO();
//            dto.setAppId(appId);
//            dto.setRootKdtId(rootKdtId);
//            kaiLeShiOrderAlignController.queryTid(dto);
//            log.info("【定时任务】【查询有赞Tid】执行成功");
//        } catch (Exception e) {
//            log.error("【定时任务】【查询有赞Tid】执行失败", e);
//        }
//    }
//
//    /**
//     * 定时任务，查询有赞订单详情
//     */
//    @Scheduled(cron = "10/20 * * * * ?")
//    public void scheduledQueryYzDetail() {
//        log.info("【定时任务】开始执行【查询有赞订单详情】...");
//        try {
//            OrderAlignDTO dto = new OrderAlignDTO();
//            dto.setAppId(appId);
//            dto.setRootKdtId(rootKdtId);
//            kaiLeShiOrderAlignController.queryYzDetail(dto);
//            log.info("【定时任务】【查询有赞订单详情】执行成功");
//        } catch (Exception e) {
//            log.error("【定时任务】【查询有赞订单详情】执行失败", e);
//        }
//    }
//
//    /**
//     * 定时任务，执行订单详情对齐
//     */
//    @Scheduled(cron = "15/20 * * * * ?")
//    public void scheduledDetailAlign() {
//        log.info("【定时任务】开始执行【订单详情对齐】...");
//        try {
//            OrderAlignDTO dto = new OrderAlignDTO();
//            dto.setAppId(appId);
//            dto.setRootKdtId(rootKdtId);
//            kaiLeShiOrderAlignController.detailAlign(dto);
//            log.info("【定时任务】【订单详情对齐】执行成功");
//        } catch (Exception e) {
//            log.error("【定时任务】【订单详情对齐】执行失败", e);
//        }
//    }
}
