package com.example.orderalign.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.orderalign.dto.OrderAlignDTO;
import com.example.orderalign.dto.OutOrderDetail;
import com.example.orderalign.dto.YzOrderDetail;
import com.example.orderalign.dto.kylin.KLSItemQueryRequest;
import com.example.orderalign.dto.kylin.KLSItemQueryResponse;
import com.example.orderalign.dto.kylin.KaileshiOrderQueryResponseDTO;
import com.example.orderalign.dto.kylin.KaileshiOrderQuerySubItemResponseDTO;
import com.example.orderalign.dto.member.OutMemberDetail;
import com.example.orderalign.mapper.*;
import com.example.orderalign.model.*;
import com.example.orderalign.service.KaiLeShiMemberAlignService;
import com.example.orderalign.utils.KaileshiUtil;
import com.example.orderalign.utils.SignUtil;
import com.youzan.cloud.connector.sdk.client.YzCloudResponse;
import com.youzan.cloud.connector.sdk.common.exception.RecoverableException;
import com.youzan.cloud.connector.sdk.common.exception.UnrecoverableException;
import com.youzan.cloud.connector.sdk.common.utils.DateFormatUtil;
import com.youzan.cloud.connector.sdk.common.utils.MoneyUtil;


import com.youzan.cloud.connector.sdk.infra.dal.entity.OrderRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.ShopRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.ShoppingGuideRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.UserRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraOrderRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraShoppingGuideRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraUserRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.ShopRelationMapper;
import com.youzan.cloud.open.sdk.gen.v1_0_1.model.YouzanScrmCustomerDetailGetResult;
import com.youzan.cloud.open.sdk.gen.v4_0_1.model.YouzanTradeGetResult;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/kaileshi")
public class KaiLeShiOrderAlignController {
    @Resource
    private KaiLeShiOrderAlignMapper kaiLeShiOrderAlignMapper;
    @Resource
    private KaiLeShiOrderRefundAlignMapper kaiLeShiOrderRefundAlignMapper;
    @Resource
    private InfraOrderRelationMapper infraOrderRelationMapper;
    @Resource
    private YouzanOrderDetailMapper youzanOrderDetailMapper;
    @Resource
    private ThirdPartyOrderDetailMapper thirdPartyOrderDetailMapper;
    @Resource
    private KaiLeShiOrderAlignResultMapper kaiLeShiOrderAlignResultMapper;
    @Resource
    private InfraUserRelationMapper infraUserRelationMapper;
    @Resource
    private ShopRelationMapper shopRelationMapper;
    @Resource
    private InfraShoppingGuideRelationMapper infraShoppingGuideRelationMapper;
    @Resource
    private NotExistItemMapper notExistItemMapper;
    private static final String ITEM_API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/product/list";
    @Resource
    private RedissonClient redissonClient;
    private static final String API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/order/page";
    private static final String API_CHANNEL_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/query";
    static OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)    // 连接超时
            .readTimeout(15, TimeUnit.SECONDS)       // 读取超时
            .writeTimeout(15, TimeUnit.SECONDS)      // 写入超时
            .build();

    private static final ExecutorService executor = new ThreadPoolExecutor(
            10,
            20,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy()
    );

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down order align executor...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        log.info("Order align executor has been shut down.");
    }


    private static final int STATUS_PENDING = 0;
    private static final int STATUS_FOUND = 1;
    private static final int STATUS_NOT_FOUND = 4;
    private static final int STATUS_DETAIL_QUERIED = 3;
    private static final int STATUS_OUT_DETAIL_QUERIED = 5;
    private static final int STATUS_ALIGNED = 6;
    private static final int BATCH_SIZE = 100;

    private static final int DETAIL_STATUS_QUERIED = 1;
    private static final int DETAIL_STATUS_YZ_FAIL = 3;
    private static final int DETAIL_STATUS_OUT_FAIL = 4;

    /**
     * 执行顺序
     * 2张表，订单映射、退单映射
     * 1、先上传数云订单号
     * 2、查询数云的订单详情，存起来；
     * 3、判断具体订单类型，查询映射表，获取订单映射，订单映射区分一下订单映射
     * 4、
     *
     * @param param
     * @return
     */
    @PostMapping("/uploadOrder")
    public YzCloudResponse<Object> uploadOrder(@RequestBody OrderAlignDTO param) {
        log.info("凯乐石订单对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> outTids = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getOutTid())) {
                outTids.add(param.getOutTid());
            }
            if (CollectionUtils.isNotEmpty(param.getOutTidList())) {
                outTids.addAll(param.getOutTidList());
            }

            if (CollectionUtils.isEmpty(outTids)) {
                return YzCloudResponse.success("outTid is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String outTid : outTids) {
                KaiLeShiOrderAlign existingLog = kaiLeShiOrderAlignMapper.selectByAppIdAndOutTid(appId, outTid);
                if (Objects.nonNull(existingLog)) {
                    log.warn("outTid: {} already exists, skipping.", outTid);
                    continue;
                }

                KaiLeShiOrderAlign tradePushLog = new KaiLeShiOrderAlign();
                tradePushLog.setAppId(appId);
                tradePushLog.setKdtId(rootKdtId);
                tradePushLog.setOutTid(outTid);
                tradePushLog.setTid(""); // tid is not provided in this scenario
                tradePushLog.setStatus(STATUS_PENDING);
                kaiLeShiOrderAlignMapper.insert(tradePushLog);
                log.info("outTid: {} inserted for processing.", outTid);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

    @PostMapping("/reAlignOrder")
    public YzCloudResponse<Object> reAlignOrder(@RequestBody OrderAlignDTO param) {
        log.info("凯乐石订单重新对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> outTids = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getOutTid())) {
                outTids.add(param.getOutTid());
            }
            if (CollectionUtils.isNotEmpty(param.getOutTidList())) {
                outTids.addAll(param.getOutTidList());
            }

            if (CollectionUtils.isEmpty(outTids)) {
                return YzCloudResponse.success("outTid is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String outTid : outTids) {
                KaiLeShiOrderAlign existingLog = kaiLeShiOrderAlignMapper.selectByAppIdAndOutTid(appId, outTid);
                if (Objects.isNull(existingLog)) {
                    log.warn("outTid: {} not exists, skipping.", outTid);
                    continue;
                }
                KaiLeShiOrderAlign updateLog = new KaiLeShiOrderAlign();
                updateLog.setId(existingLog.getId());
                updateLog.setStatus(3);
                kaiLeShiOrderAlignMapper.update(updateLog);
                log.info("outTid: {} updated for processing.", outTid);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

    @PostMapping("/reAlignOrder2")
    public YzCloudResponse<Object> reAlignOrder2(@RequestBody OrderAlignDTO param) {
        log.info("凯乐石订单重新对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> outTids = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getOutTid())) {
                outTids.add(param.getOutTid());
            }
            if (CollectionUtils.isNotEmpty(param.getOutTidList())) {
                outTids.addAll(param.getOutTidList());
            }

            if (CollectionUtils.isEmpty(outTids)) {
                return YzCloudResponse.success("outTid is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String outTid : outTids) {
                KaiLeShiOrderAlign existingLog = kaiLeShiOrderAlignMapper.selectByAppIdAndOutTid(appId, outTid);
                if (Objects.isNull(existingLog)) {
                    log.warn("outTid: {} not exists, skipping.", outTid);
                    continue;
                }

                KaiLeShiOrderAlign updateLog = new KaiLeShiOrderAlign();
                updateLog.setId(existingLog.getId());
                updateLog.setStatus(3);
                kaiLeShiOrderAlignMapper.update(updateLog);
                int i = kaiLeShiOrderAlignResultMapper.deleteByOutTid(appId, existingLog.getOutTid());
                log.info("outTid: {} updated for processing.", outTid);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

    /**
     * 删除订单后，重新创建后对账
     * @param param
     * @return
     */
    @PostMapping("/initAlignOrder")
    public YzCloudResponse<Object> updateToInit(@RequestBody OrderAlignDTO param) {
        log.info("凯乐石订单重新对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> outTids = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getOutTid())) {
                outTids.add(param.getOutTid());
            }
            if (CollectionUtils.isNotEmpty(param.getOutTidList())) {
                outTids.addAll(param.getOutTidList());
            }

            if (CollectionUtils.isEmpty(outTids)) {
                return YzCloudResponse.success("outTid is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String outTid : outTids) {
                KaiLeShiOrderAlign existingLog = kaiLeShiOrderAlignMapper.selectByAppIdAndOutTid(appId, outTid);
                if (Objects.isNull(existingLog)) {
                    log.warn("outTid: {} not exists, skipping.", outTid);
                    continue;
                }
                KaiLeShiOrderAlign updateLog = new KaiLeShiOrderAlign();
                updateLog.setId(existingLog.getId());
                updateLog.setStatus(5);
                updateLog.setTid(" ");
                kaiLeShiOrderAlignMapper.update(updateLog);
                log.info("outTid: {} updated for processing.", outTid);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

    /**
     * 初始化三方订单详情，以及orderRelation 和 refundRelation
     *
     * @param param
     * @return
     */
    @SneakyThrows
    @PostMapping("/queryOutDetail")
    public YzCloudResponse<Object> queryOutDetail(@RequestBody OrderAlignDTO param) {
        log.info("开始查询订单详情");
        String appId = param.getAppId();
        String[] appIdArr = appId.split("_");
        String tripartite = appIdArr[1];
        Long rootKdtId = param.getRootKdtId();
//        Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);

        String lockKey = String.format("queryDetail_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},订单详情查询正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }

        try {
            List<KaiLeShiOrderAlign> pendingOrders = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(STATUS_PENDING, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String outTid = orderAlign.getOutTid();

                            ThirdPartyOrderDetail thirdPartyOrderDetail = new ThirdPartyOrderDetail();
                            thirdPartyOrderDetail.setAppId(appId);
                            thirdPartyOrderDetail.setKdtId(rootKdtId);
                            thirdPartyOrderDetail.setOutTid(outTid);

                            KaileshiOrderQueryResponseDTO kaileshiOrderQueryResponse = new KaileshiOrderQueryResponseDTO();
                            String kylinOrderDetailStr = kylinOrderDetailQuery(outTid);
                            if (StringUtils.isNotBlank(kylinOrderDetailStr)) {
                                JSONArray data = JSON.parseObject(kylinOrderDetailStr).getJSONArray("data");
                                if (!data.isEmpty()) {
                                    kaileshiOrderQueryResponse = JSON.parseObject(JSON.toJSONString(data.getJSONObject(0)), KaileshiOrderQueryResponseDTO.class);
                                } else {
                                    orderAlign.setStatus(STATUS_NOT_FOUND);
                                    kaiLeShiOrderAlignMapper.update(orderAlign);
                                    log.warn("查询数云订单详情失败, outTid: {}", outTid);
                                    return;
                                }
                            }
                            if (Objects.isNull(kaileshiOrderQueryResponse)) {
                                orderAlign.setStatus(STATUS_NOT_FOUND);
                                kaiLeShiOrderAlignMapper.update(orderAlign);
                                log.warn("查询数云订单详情失败, outTid: {}", outTid);
                                return;
                            }

                            List<KaileshiOrderQuerySubItemResponseDTO> exchangeOrderItems = new ArrayList<>();
                            List<KaileshiOrderQuerySubItemResponseDTO> refundOrderItems = new ArrayList<>();
                            List<KaileshiOrderQuerySubItemResponseDTO> noSourceRefundOrderItems = new ArrayList<>();
                            List<KaileshiOrderQuerySubItemResponseDTO> normalOrderItems = new ArrayList<>();
                            String orderId = kaileshiOrderQueryResponse.getOrderId();
                            List<KaileshiOrderQuerySubItemResponseDTO> orderItems = kaileshiOrderQueryResponse.getOrderItems();
                            //判断是否是换货单
                            boolean isExchangeOrder = false;
                            for (KaileshiOrderQuerySubItemResponseDTO orderItem : orderItems) {
                                if (orderItem.getQuantity() < 0) {
                                    isExchangeOrder = true;
                                    break;
                                }
                            }
                            String originOrderId = kaileshiOrderQueryResponse.getOriginOrderId();
                            //拆分子订单类型
                            for (KaileshiOrderQuerySubItemResponseDTO orderItem : orderItems) {
                                handleOrderItemType(isExchangeOrder, orderId, originOrderId, orderItem, exchangeOrderItems, refundOrderItems, noSourceRefundOrderItems, normalOrderItems);
                            }

                            //有原单退单
                            if (CollectionUtils.isNotEmpty(refundOrderItems) || CollectionUtils.isNotEmpty(noSourceRefundOrderItems)) {
                                KaiLeShiOrderRefundAlign tradePushLog = new KaiLeShiOrderRefundAlign();
                                tradePushLog.setAppId(appId);
                                tradePushLog.setKdtId(rootKdtId);
                                tradePushLog.setOutRefundId(orderId);
                                tradePushLog.setRefundId(""); // refundId is not provided in this scenario
                                tradePushLog.setStatus(STATUS_OUT_DETAIL_QUERIED);
                                tradePushLog.setType("正向退单");
                                if (CollectionUtils.isNotEmpty(noSourceRefundOrderItems)) {
                                    tradePushLog.setIsNoSourceRefundOrder(1);
                                } else {
                                    tradePushLog.setIsNoSourceRefundOrder(0);
                                }
                                kaiLeShiOrderRefundAlignMapper.insert(tradePushLog);
                            }

                            OutOrderDetail outOrderDetail = new OutOrderDetail();
                            outOrderDetail.setChannel(kaileshiOrderQueryResponse.getChannelType());
                            outOrderDetail.setOutTid(outTid);
                            outOrderDetail.setCustomerNo(kaileshiOrderQueryResponse.getCustomerNo());
                            outOrderDetail.setMemberId(kaileshiOrderQueryResponse.getMemberId());
                            outOrderDetail.setShopCode(kaileshiOrderQueryResponse.getShopCode());
                            outOrderDetail.setGuideCode(kaileshiOrderQueryResponse.getGuideCode());
                            outOrderDetail.setTotalAmount(MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getTotalFee()));
                            outOrderDetail.setTotalPayAmount(MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getPayment()));
                            outOrderDetail.setCreateTime(kaileshiOrderQueryResponse.getOrderTime());
                            outOrderDetail.setPayTime(kaileshiOrderQueryResponse.getPayTime());
                            outOrderDetail.setTotalDiscountAmount(MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getTotalFee()) - MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getPayment()));

                            List<OutOrderDetail.SubOrder> oidList = new ArrayList<>();

                            for (KaileshiOrderQuerySubItemResponseDTO orderItem : orderItems) {
                                OutOrderDetail.SubOrder subOrder = new OutOrderDetail.SubOrder();
                                subOrder.setNum(orderItem.getQuantity());
                                subOrder.setTotalFee(MoneyUtil.Yuan2Cent(orderItem.getTotalFee()));
                                subOrder.setPayment(MoneyUtil.Yuan2Cent(orderItem.getPayment()));
                                subOrder.setItemNo(orderItem.getProductCode());
                                subOrder.setSkuNo(orderItem.getSkuId());
                                subOrder.setTitle(orderItem.getProductName());
                                subOrder.setOutOid(orderItem.getOrderItemId());
                                oidList.add(subOrder);
                            }
                            outOrderDetail.setOidList(oidList);
                            thirdPartyOrderDetail.setOutTidDetail(JSON.toJSONString(outOrderDetail));
                            thirdPartyOrderDetail.setStatus(DETAIL_STATUS_QUERIED);
                            thirdPartyOrderDetailMapper.insert(thirdPartyOrderDetail);
                            //三方详情已查询
                            orderAlign.setStatus(STATUS_OUT_DETAIL_QUERIED);
                            orderAlign.setType("正常订单");
                            kaiLeShiOrderAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutTid(), e);
                        }
                    }, executor))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("查询Detail任务失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        } finally {
            lock.unlock();
        }
        log.info("查询订单Detail任务结束");
        return YzCloudResponse.success();
    }

    @SneakyThrows
    @PostMapping("/queryTid")
    public YzCloudResponse<Object> queryTid(@RequestBody OrderAlignDTO param) {
        log.info("开始查询订单Tid,appId:{}", param.getAppId());
        String lockKey = String.format("queryTid_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},订单映射对齐正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new UnrecoverableException("获取锁失败");
        }

        try {
            String appId = param.getAppId();
            List<KaiLeShiOrderAlign> pendingOrders = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(STATUS_OUT_DETAIL_QUERIED, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            OrderRelationDO orderRelation = infraOrderRelationMapper.getOne(orderAlign.getAppId(), null, orderAlign.getOutTid());
                            if (Objects.nonNull(orderRelation) && StringUtils.isNotBlank(orderRelation.getTid())) {
                                orderAlign.setStatus(STATUS_FOUND); // Found
                                orderAlign.setTid(orderRelation.getTid());
                                log.info("outTid: {} 找到 tid: {}", orderAlign.getOutTid(), orderRelation.getTid());
                            } else {
                                String tid = queryTid(orderAlign.getOutTid());
                                if (StringUtils.isNotBlank(tid)) {
                                    orderAlign.setStatus(STATUS_FOUND); // Found
                                    orderAlign.setTid(tid);
                                } else {
                                    orderAlign.setStatus(2); // Not found
                                    log.warn("outTid: {} 未找到 tid", orderAlign.getOutTid());
                                }
                            }
                            kaiLeShiOrderAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutTid(), e);
                        }
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("查询Tid任务失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        } finally {
            lock.unlock();
        }
        log.info("查询订单Tid任务结束");
        return YzCloudResponse.success();
    }

    @SneakyThrows
    @PostMapping("/queryYzDetail")
    public YzCloudResponse<Object> queryYzDetail(@RequestBody OrderAlignDTO param) {
        log.info("开始查询订单详情");
        String appId = param.getAppId();
        String[] appIdArr = appId.split("_");
        String tripartite = appIdArr[1];
        Long rootKdtId = param.getRootKdtId();
//        Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);

        String lockKey = String.format("queryYzDetail_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},订单详情查询正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }

        try {
            List<KaiLeShiOrderAlign> pendingOrders = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(STATUS_FOUND, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String tid = orderAlign.getTid();
                            YouzanOrderDetail youzanOrderDetail = youzanOrderDetailMapper.selectByTid(appId, tid);
                            if (Objects.nonNull(youzanOrderDetail) && StringUtils.isNotBlank(youzanOrderDetail.getTidDetail())) {
                                orderAlign.setStatus(STATUS_DETAIL_QUERIED);
                                kaiLeShiOrderAlignMapper.update(orderAlign);
                                return;
                            }
                            if (Objects.nonNull(youzanOrderDetail) && StringUtils.isBlank(youzanOrderDetail.getTidDetail())) {
                                youzanOrderDetailMapper.deleteByPrimaryKey(youzanOrderDetail.getId());
                            }
                            youzanOrderDetail = new YouzanOrderDetail();
                            youzanOrderDetail.setAppId(appId);
                            youzanOrderDetail.setKdtId(rootKdtId);
                            youzanOrderDetail.setTid(tid);

                            if (StringUtils.isNotBlank(tid)) {
                                String detailStr = queryDetail(tid);
                                YouzanTradeGetResult youzanTradeGetResult = JSON.parseObject(detailStr, YouzanTradeGetResult.class);
                                if (!youzanTradeGetResult.getSuccess() || Objects.isNull(youzanTradeGetResult.getData())) {
                                    youzanOrderDetail.setStatus(DETAIL_STATUS_YZ_FAIL);
                                    youzanOrderDetailMapper.insert(youzanOrderDetail);
                                    orderAlign.setStatus(STATUS_NOT_FOUND);
                                    kaiLeShiOrderAlignMapper.update(orderAlign);
                                    log.warn("查询有赞订单详情失败, tid: {}", tid);
                                    return;
                                }
//                                JSONObject jsonData = JSON.parseObject(detailStr);
                                YouzanTradeGetResult.YouzanTradeGetResultData data = youzanTradeGetResult.getData();
                                YouzanTradeGetResult.YouzanTradeGetResultFullorderinfo fullOrderInfo = data.getFullOrderInfo();
                                YouzanTradeGetResult.YouzanTradeGetResultOrderinfo yzOrderInfo = fullOrderInfo.getOrderInfo();
                                YzOrderDetail yzOrderDetail = new YzOrderDetail();
                                yzOrderDetail.setTid(tid);
                                yzOrderDetail.setKdtId(yzOrderInfo.getNodeKdtId());
                                yzOrderDetail.setMobile(fullOrderInfo.getBuyerInfo().getBuyerPhone());
                                yzOrderDetail.setYzOpenId(fullOrderInfo.getBuyerInfo().getYzOpenId());
                                yzOrderDetail.setChannel(yzOrderInfo.getOrderExtra().getOpenSource());
                                yzOrderDetail.setCreateTime(DateFormatUtil.parseDate2Str(yzOrderInfo.getCreated()));
                                yzOrderDetail.setPayTime(DateFormatUtil.parseDate2Str(yzOrderInfo.getPayTime()));
                                yzOrderDetail.setTotalAmount(MoneyUtil.YuanStr2Cent(fullOrderInfo.getPayInfo().getTotalFee()));
                                yzOrderDetail.setTotalPayAmount(MoneyUtil.YuanStr2Cent(fullOrderInfo.getPayInfo().getPayment()));
                                yzOrderDetail.setTotalDiscountAmount(MoneyUtil.YuanStr2Cent(fullOrderInfo.getPayInfo().getTotalFee()) - MoneyUtil.YuanStr2Cent(fullOrderInfo.getPayInfo().getPayment()));

                                List<YzOrderDetail.SubOrder> yzOidList = new ArrayList<>();
//                                JSONArray jsonOrders = jsonData.getJSONObject("data").getJSONObject("full_order_info").getJSONArray("orders");
//                                for (int i = 0; i < jsonOrders.size(); i++) {
//                                    JSONObject jsonOrder = jsonOrders.getJSONObject(i);
//                                    String itemNo = jsonOrder.getString("item_no");
//                                    String skuNo = jsonOrder.getString("sku_no");
//                                    String itemBarcode = jsonOrder.getString("item_barcode");
//                                    String skuBarcode = jsonOrder.getString("sku_barcode");
//
//                                    YzOrderDetail.SubOrder subOrder = new YzOrderDetail.SubOrder();
//                                    subOrder.setItemId(jsonOrder.getLong("item_id"));
//                                    subOrder.setSkuId(jsonOrder.getLong("sku_id"));
//                                    subOrder.setItemNo(StringUtils.isEmpty(itemNo) ? itemBarcode : itemNo);
//                                    subOrder.setSkuNo(StringUtils.isEmpty(skuNo) ? skuBarcode : skuNo);
//                                    subOrder.setNum(jsonOrder.getInteger("num"));
//                                    subOrder.setPrice(MoneyUtil.YuanStr2Cent(jsonOrder.getString("price")));
//                                    subOrder.setDiscountPrice(MoneyUtil.YuanStr2Cent(jsonOrder.getString("discount_price")));
//                                    subOrder.setTotalAmount(MoneyUtil.YuanStr2Cent(jsonOrder.getString("total_fee")));
//                                    subOrder.setPayment(MoneyUtil.YuanStr2Cent(jsonOrder.getString("payment")));
//                                    subOrder.setTitle(jsonOrder.getString("title"));
//                                    subOrder.setOutOid(jsonOrder.getString("outer_oid"));
//
//                                    JSONArray daogousArray = jsonOrder.getJSONArray("daogous");
//                                    if (Objects.nonNull(daogousArray) && daogousArray.size() > 0) {
//                                        List<String> daogousList = new ArrayList<>();
//                                        for (int j = 0; j < daogousArray.size(); j++) {
//                                            daogousList.add(daogousArray.getString(j));
//                                        }
//                                        subOrder.setDaogous(daogousList);
//                                    }
//
//                                    yzOidList.add(subOrder);
//                                }
                                List<YouzanTradeGetResult.YouzanTradeGetResultOrders> orders = data.getFullOrderInfo().getOrders();
                                for (YouzanTradeGetResult.YouzanTradeGetResultOrders order : orders) {
                                    String itemNo = order.getItemNo();
                                    String skuNo = order.getSkuNo();
                                    String itemBarcode = order.getItemBarcode();
                                    String skuBarcode = order.getSkuBarcode();

                                    YzOrderDetail.SubOrder subOrder = new YzOrderDetail.SubOrder();
                                    subOrder.setItemId(order.getItemId());
                                    subOrder.setSkuId(order.getSkuId());
                                    subOrder.setItemNo(StringUtils.isEmpty(itemNo) ? itemBarcode : itemNo);
                                    subOrder.setSkuNo(StringUtils.isEmpty(skuNo) ? skuBarcode : skuNo);
                                    subOrder.setNum(order.getNum());
                                    subOrder.setPrice(MoneyUtil.YuanStr2Cent(order.getPrice()));
                                    subOrder.setDiscountPrice(MoneyUtil.YuanStr2Cent(order.getDiscountPrice()));
                                    subOrder.setTotalAmount(MoneyUtil.YuanStr2Cent(order.getTotalFee()));
                                    subOrder.setPayment(MoneyUtil.YuanStr2Cent(order.getPayment()));
                                    subOrder.setTitle(order.getTitle());
                                    subOrder.setOutOid(order.getOuterOid());
                                    subOrder.setDaogous(order.getDaogous());
                                    yzOidList.add(subOrder);
                                }
                                yzOrderDetail.setOidList(yzOidList);
                                youzanOrderDetail.setTidDetail(JSON.toJSONString(yzOrderDetail));
                                youzanOrderDetail.setStatus(DETAIL_STATUS_QUERIED);
                                youzanOrderDetailMapper.insert(youzanOrderDetail);
                            }

                            orderAlign.setStatus(STATUS_DETAIL_QUERIED);
                            kaiLeShiOrderAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            orderAlign.setStatus(7);
                            kaiLeShiOrderAlignMapper.update(orderAlign);
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutTid(), e);
                        }
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("查询Detail任务失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        } finally {
            lock.unlock();
        }
        log.info("查询订单Detail任务结束");
        return YzCloudResponse.success();
    }

    @SneakyThrows
    @PostMapping("/detailAlign")
    public YzCloudResponse<Object> detailAlign(@RequestBody OrderAlignDTO param) {
        log.info("开始订单详情对齐");
        String appId = param.getAppId();
        String lockKey = String.format("detailAlign_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},订单详情对齐正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }
        try {
            List<KaiLeShiOrderAlign> pendingOrders = kaiLeShiOrderAlignMapper.selectByStatusWithLimit(STATUS_DETAIL_QUERIED, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }
            String[] appIdArr = appId.split("_");
            String tripartite = appIdArr[1];
            Long rootKdtId = param.getRootKdtId();
//            Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);
            List<String> pendingTids = pendingOrders.stream().map(KaiLeShiOrderAlign::getOutTid).collect(Collectors.toList());
            log.info("本批次处理订单数量: {}, 订单号: {}", pendingOrders.size(), JSON.toJSONString(pendingTids));

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String tid = orderAlign.getTid();
                            String outTid = orderAlign.getOutTid();
                            log.info("开始处理单号:{}", tid);
                            List<KaiLeShiOrderAlignResult> kaiLeShiOrderAlignResults = kaiLeShiOrderAlignResultMapper.selectByOutTid(appId, outTid);
                            if (CollectionUtils.isNotEmpty(kaiLeShiOrderAlignResults)) {
                                for(KaiLeShiOrderAlignResult kaiLeShiOrderAlignResult : kaiLeShiOrderAlignResults) {
                                    Long id = kaiLeShiOrderAlignResult.getId();
                                    kaiLeShiOrderAlignResultMapper.deleteByPrimaryKey(id);
                                }
                            }
                            YouzanOrderDetail youzanOrderDetail = youzanOrderDetailMapper.selectByTid(appId, tid);
                            ThirdPartyOrderDetail thirdPartyOrderDetail = thirdPartyOrderDetailMapper.selectByOutTid(appId, outTid);

                            if (Objects.isNull(youzanOrderDetail) || StringUtils.isBlank(youzanOrderDetail.getTidDetail())) {
                                log.error("有赞订单详情不存在, tid: {}", tid);
                                orderAlign.setStatus(4);
                                kaiLeShiOrderAlignMapper.update(orderAlign);
                                return;
                            }
                            if (Objects.isNull(thirdPartyOrderDetail) || StringUtils.isBlank(thirdPartyOrderDetail.getOutTidDetail())) {
                                log.error("三方订单详情不存在, outTid: {}", outTid);
                                orderAlign.setStatus(4);
                                kaiLeShiOrderAlignMapper.update(orderAlign);
                                return;
                            }

                            YzOrderDetail yzOrderDetail = JSON.parseObject(youzanOrderDetail.getTidDetail(), YzOrderDetail.class);
                            OutOrderDetail outOrderDetail = JSON.parseObject(thirdPartyOrderDetail.getOutTidDetail(), OutOrderDetail.class);

                            KaiLeShiOrderAlignResult result = new KaiLeShiOrderAlignResult();

                            result.setKdtId(rootKdtId);
                            result.setAppId(appId);
                            result.setTid(tid);
                            result.setOutTid(outTid);

                            //创建时间对齐
                            result.setYzCreateTime(yzOrderDetail.getCreateTime());
                            result.setOutCreateTime(outOrderDetail.getCreateTime());

                            Date createDate = KaileshiUtil.convertTime2UTC8DateUtil(outOrderDetail.getCreateTime());
                            String createStr = DateFormatUtil.parseDate2Str(createDate);
                            result.setCreateTimeResult(String.valueOf(Objects.equals(yzOrderDetail.getCreateTime(), createStr)));

                            //支付时间对齐
                            result.setYzPayTime(yzOrderDetail.getPayTime());
                            result.setOutPayTime(outOrderDetail.getPayTime());
                            Date payDate = KaileshiUtil.convertTime2UTC8DateUtil(outOrderDetail.getPayTime());
                            if (payDate != null && createDate != null && payDate.before(createDate)) {
                                payDate = createDate;
                            }
                            String payDateStr = DateFormatUtil.parseDate2Str(payDate);
                            result.setPayTimeResult(String.valueOf(Objects.equals(yzOrderDetail.getPayTime(), payDateStr)));
                            if (StringUtils.isBlank(outOrderDetail.getPayTime())) {
                                result.setPayTimeResult("true");
                            }

                            Long outOrderTotalAmount = outOrderDetail.getTotalAmount();
                            Long outOrderTotalPayAmount = outOrderDetail.getTotalPayAmount();
                            Long outOrderTotalDiscountAmount = outOrderDetail.getTotalAmount() - outOrderDetail.getTotalPayAmount();
                            Long outTotalAmount = 0L;
                            Long outTotalPayAmount = 0L;
                            Long outTotalDiscountAmount = 0L;


                            // Member alignment
                            result.setYzMemberId(yzOrderDetail.getYzOpenId());
                            result.setCustomerNo(outOrderDetail.getCustomerNo());
                            result.setOutMemberId(outOrderDetail.getMemberId());

                            //有赞id先检验，是否有效
                            if (StringUtils.isNotBlank(yzOrderDetail.getMobile())) {
                                String yzOpenIdQueryStr = queryYzOpenId(yzOrderDetail.getMobile());
                                YouzanScrmCustomerDetailGetResult customerDetailGetResult = JSON.parseObject(yzOpenIdQueryStr, YouzanScrmCustomerDetailGetResult.class);
                                String newestYzOpenId = customerDetailGetResult.getData().getYzOpenId();
                                if (!Objects.equals(yzOrderDetail.getYzOpenId(), newestYzOpenId)) {
                                    //产生了换绑
                                    result.setMemberIdResult("true");
                                }
                            } else {
                                if (StringUtils.isNotBlank(yzOrderDetail.getYzOpenId())) {
                                    String yzOpenIdQueryStr = queryBzYzOpenId(yzOrderDetail.getYzOpenId());
                                    YouzanScrmCustomerDetailGetResult customerDetailGetResult = JSON.parseObject(yzOpenIdQueryStr, YouzanScrmCustomerDetailGetResult.class);
                                    if (Objects.isNull(customerDetailGetResult.getData())) {
                                        //产生了注销
                                        result.setMemberIdResult("true");
                                    }
                                }
                            }
                            if (!Objects.equals(result.getMemberIdResult(), "true")) {
                                UserRelationDO userRelation = infraUserRelationMapper.getByYzOpenId(appId, rootKdtId, yzOrderDetail.getYzOpenId());
                                if (Objects.nonNull(userRelation)) {
                                    String outOpenId = userRelation.getOutOpenId();
                                    result.setOutOpenId(outOpenId);
                                    //渠道用户id比对
                                    if (StringUtils.isNotBlank(outOpenId) && !outOpenId.startsWith("K")) {
                                        result.setCustomerNo(outOrderDetail.getCustomerNo());
                                        result.setMemberIdResult(String.valueOf(Objects.equals(outOpenId, result.getCustomerNo())));
                                    } else {
                                        //会员id比对
                                        result.setOutMemberId(outOrderDetail.getMemberId());
                                        if (StringUtils.isBlank(outOrderDetail.getMemberId()) && StringUtils.isNotBlank(outOrderDetail.getCustomerNo())) {
                                            //三方会员ID为空，有赞映射不为空，按映射查询渠道ID,看是否包含该customerNo
                                            String channelQueryStr = memberChannelQuery(outOpenId);
                                            JSONObject channelQueryObj = JSON.parseObject(channelQueryStr);
                                            if (Objects.nonNull(channelQueryObj.getJSONObject("data"))) {
                                                JSONArray channelInfoList = channelQueryObj.getJSONObject("data").getJSONArray("channelInfoList");
                                                for (int i = 0; i < channelInfoList.size(); i++) {
                                                    String customerNo = channelInfoList.getJSONObject(i).getString("customerNo");
                                                    if (Objects.equals(customerNo, outOrderDetail.getCustomerNo())) {
                                                        result.setMemberIdResult("true");
                                                        break;
                                                    }
                                                }
                                            }
                                        } else {
                                            result.setMemberIdResult(String.valueOf(Objects.equals(outOpenId, result.getOutMemberId())));
                                        }
                                    }
                                } else {
                                    if (StringUtils.isBlank(outOrderDetail.getMemberId())) {
                                        //非会员单，视为一致
                                        result.setMemberIdResult("true");
                                    }

                                    if (StringUtils.isNotBlank(outOrderDetail.getMemberId())) {
                                        String kylinMemberDetailStr = memberQuery(outOrderDetail.getMemberId());
                                        if (StringUtils.isNotBlank(kylinMemberDetailStr)) {
                                            JSONArray data = JSON.parseObject(kylinMemberDetailStr).getJSONArray("data");
                                            if (Objects.nonNull(data) && data.size() > 0) {
                                                OutMemberDetail outMemberDetail = JSON.parseObject(JSON.toJSONString(JSON.parseObject(kylinMemberDetailStr).getJSONArray("data").getJSONObject(0)), OutMemberDetail.class);
                                                String mobile = outMemberDetail.getMobile();
                                                if (StringUtils.isBlank(mobile)) {
                                                    //会员无手机号，视为一致
                                                    result.setMemberIdResult("true");
                                                }
                                            }
                                        }
                                    }
                                    if (!Objects.equals(result.getMemberIdResult(), "true")) {
                                        result.setMemberIdResult("会员映射不存在");
                                    }
                                }
                            }

                            // Channel alignment
                            result.setYzChannel(yzOrderDetail.getChannel());
                            JSONObject jsonObject = JSON.parseObject(yzOrderDetail.getChannel());
                            String channelStr = jsonObject.getJSONObject("tradeChannel").getString("commonChannel");
                            String finalChannelStr = "";
                            switch (channelStr) {
                                case "Taobao":
                                    finalChannelStr = "TAOBAO";
                                    break;
                                case "douyin":
                                    finalChannelStr = "DOUYIN";
                                    break;
                                case "POS":
                                    finalChannelStr = "POS";
                                    break;
                                case "JD":
                                    finalChannelStr = "JD";
                                    break;
                                default:
                                    finalChannelStr = channelStr;
                                    break;
                            }
                            result.setOutChannel(outOrderDetail.getChannel());
                            String outChannel = outOrderDetail.getChannel();
                            if (Objects.equals("O2O", outChannel) || Objects.equals("E3", outChannel)) {
                                outChannel = "POS";
                            }
                            result.setChannelResult(String.valueOf(Objects.equals(finalChannelStr, outChannel)));

                            // Shop alignment
                            Long kdtId = yzOrderDetail.getKdtId();
                            result.setNodeKdtId(kdtId);
                            result.setOutShopNo(outOrderDetail.getShopCode());
                            List<ShopRelationDO> shopRelationList = shopRelationMapper.getByBranchId(appId, kdtId, "UP");
                            if (CollectionUtils.isNotEmpty(shopRelationList)) {
                                result.setYzShopNo(shopRelationList.get(0).getOutBranchId());
                                result.setOutShopNo(outOrderDetail.getShopCode());
                                result.setShopResult(String.valueOf(Objects.equals(result.getYzShopNo(), result.getOutShopNo())));
                            } else {
                                result.setOutShopNo(outOrderDetail.getShopCode());
                                result.setShopResult("店铺未映射");
                            }
                            List<YzOrderDetail.SubOrder> oidList = yzOrderDetail.getOidList();
                            //subOrder.num
                            //subOrder.price
                            //subOrder.discountPrice
                            //subOrder.totalAmount
                            //subOrder.payment
                            //subOrder.itemNo
                            //subOrder.skuNo
                            //subOrder.guide
                            List<OutOrderDetail.SubOrder> outOidList = outOrderDetail.getOidList();
                            boolean itemAlign = true;
                            StringBuilder sb = new StringBuilder("");
                            String type = orderAlign.getType();
                            List<String> guideNos = new ArrayList<>();
                            for (OutOrderDetail.SubOrder outOrder : outOidList) {
                                Integer outNum = outOrder.getNum();
                                //按订单类型来区分取值逻辑
                                if (Objects.equals(type, "正常订单")) {
                                    if (outNum <= 0) {
                                        continue;
                                    }
                                } else {
                                    if (outNum >= 0) {
                                        continue;
                                    }
                                }

                                String outItemNo = outOrder.getItemNo();
                                //转换69码
                                String outSkuNo = outOrder.getSkuNo();
                                if (StringUtils.isNotBlank(outSkuNo)) {
                                    RBucket<String> skuCodeBucket = redissonClient.getBucket(outSkuNo);
                                    String skuCode = skuCodeBucket.get();
                                    if (StringUtils.isBlank(skuCode)) {
                                        String eanCode = queryEanCode(outItemNo, outSkuNo);
                                        skuCode = parseEanCode(eanCode, outSkuNo);
                                        skuCodeBucket.set(skuCode, 2, TimeUnit.DAYS);
                                        outSkuNo = skuCode;
                                    } else {
                                        outSkuNo = skuCode;
                                    }
                                }

                                String yzOutOid = outOrder.getOutOid();
                                String outTitle = outOrder.getTitle();
                                Long totalFee = Math.abs(outOrder.getTotalFee());
                                outTotalAmount += totalFee;
                                Long outPayment = Math.abs(outOrder.getPayment());
                                outTotalPayAmount += outPayment;
                                outNum = Math.abs(outNum);
                                // 商品原价
                                Long outPrice = KaileshiUtil.handlePrice(MoneyUtil.centToYuan(totalFee).doubleValue(), outNum);
                                // 单商品现价（原价减去优惠后的金额）
                                Long outDiscountPrice = KaileshiUtil.handlePrice(MoneyUtil.centToYuan(outPayment).doubleValue(), outNum);
                                outTotalDiscountAmount += totalFee - outPayment;
                                boolean itemNoAlign = true;
                                boolean itemNumAlign = true;
                                boolean itemPriceAlign = true;
                                boolean itemDiscountPriceAlign = true;
                                boolean itemTotalAmountAlign = true;
                                boolean itemPaymentAlign = true;
                                boolean itemTitleAlign = true;
//                        boolean guideAlign = true;
                                result.setIsMockItemId("false");
                                for (YzOrderDetail.SubOrder yzOid : oidList) {
                                    String outOid = yzOid.getOutOid();
                                    if (Objects.equals(yzOutOid, outOid)) {
                                        Long itemId = yzOid.getItemId();
                                        Long skuId = yzOid.getSkuId();
                                        String yzItemNo = yzOid.getItemNo();
                                        String yzSkuNo = yzOid.getSkuNo();
                                        NotExistItem notExistItem = notExistItemMapper.selectByKdtIdAndItemNoAndSkuNo(42243307L, outItemNo, outSkuNo);
                                        //虚拟商品
                                        if (Objects.nonNull(notExistItem)) {
                                            itemNoAlign = true;

//                                            if (Objects.equals(outItemNo, yzItemNo) || Objects.equals(outSkuNo, yzSkuNo)) {
//                                                //有赞不为69开头，三方为69开头，视为一致
//
//                                            }
                                        } else {
                                            if (StringUtils.isBlank(outSkuNo) && StringUtils.isNotBlank(yzSkuNo)) {
                                                itemNoAlign = false;
                                            } else if (Objects.equals(outItemNo, yzItemNo) && StringUtils.isNotBlank(outSkuNo) && !yzSkuNo.startsWith("69") && outSkuNo.startsWith("69")) {
                                                //有赞不为69开头，三方为69开头，视为一致
                                                itemNoAlign = true;
                                            } else if (!(Objects.equals(outItemNo, yzItemNo) && Objects.equals(outSkuNo, yzSkuNo))) {
                                                itemNoAlign = false;
                                            }
                                            if (itemId == 1 || skuId == 1) {
                                                result.setIsMockItemId("true");
                                            }
                                        }

                                        Integer yzNum = yzOid.getNum();
                                        if (!outNum.equals(yzNum)) {
                                            itemNumAlign = false;
                                        }

                                        Long price = yzOid.getPrice();
                                        if (!Objects.equals(outPrice, price)) {
                                            itemPriceAlign = false;
                                        }

                                        String yzTitle = yzOid.getTitle();
                                        if (!Objects.equals(yzTitle, outTitle)) {
                                            itemTitleAlign = false;
                                        }
                                        Long discountPrice = yzOid.getDiscountPrice();
                                        if (!Objects.equals(outDiscountPrice, discountPrice)) {
                                            itemDiscountPriceAlign = false;
                                        }
                                        List<String> daogous = yzOid.getDaogous();
                                        if (CollectionUtils.isNotEmpty(daogous)) {
                                            guideNos.addAll(daogous);
                                        }
                                        Long payment = yzOid.getPayment();
                                        if (!Objects.equals(payment, outPayment)) {
                                            itemPaymentAlign = false;
                                        }
                                        if (!itemNoAlign || !itemNumAlign || !itemPriceAlign || !itemDiscountPriceAlign || !itemTotalAmountAlign || !itemPaymentAlign) {
                                            sb.append(outOid);
                                            if (!itemNoAlign) {
                                                sb.append("商品或规格编码不一致;");
                                                sb.append("有赞商品编码:" + yzItemNo + ";");
                                                sb.append("数云商品编码:" + outItemNo + ";");
                                                sb.append("有赞规格编码:" + yzSkuNo + ";");
                                                sb.append("数云规格编码:" + outSkuNo + ";");
                                                sb.append("有赞商品ID:" + itemId + ";");
                                                sb.append("有赞规格ID:" + skuId + ";");
                                            }
                                            if (!itemNumAlign) {
                                                sb.append("下单数量不一致;");
                                            }
                                            if (!itemPriceAlign) {
                                                sb.append("商品原价不一致;");
                                            }
                                            if (!itemDiscountPriceAlign) {
                                                sb.append("商品现价不一致;");
                                            }
                                            if (!itemTotalAmountAlign) {
                                                sb.append("商品应付总额不一致;");
                                            }
                                            if (!itemPaymentAlign) {
                                                sb.append("商品实付总额不一致;");
                                            }
                                            if (!itemTitleAlign) {
                                                sb.append("商品名称不一致;");
                                            }
                                        }
                                    }
                                }
                                if (!itemNoAlign || !itemNumAlign || !itemPriceAlign || !itemDiscountPriceAlign || !itemTotalAmountAlign || !itemPaymentAlign) {
                                    itemAlign = false;
                                }
                            }
                            if (!itemAlign) {
                                result.setSubOrderResult("子订单不一致");
                                result.setSubOrderFailReason(sb.toString());
                            } else {
                                result.setSubOrderResult("子订单一致");
                            }

                            //应付金额
                            result.setYzTotalAmount(yzOrderDetail.getTotalAmount());

                            //先比对订单级别的应付总额
                            if (Objects.equals(yzOrderDetail.getTotalAmount(), outOrderTotalAmount)) {
                                result.setTotalAmountResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalAmount(), outOrderTotalAmount)));
                                result.setOutTotalAmount(outOrderTotalAmount);
                            } else {
                                result.setTotalAmountResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalAmount(), outTotalAmount)));
                                result.setOutTotalAmount(outTotalAmount);
                            }

                            //实付金额
                            result.setYzPayment(yzOrderDetail.getTotalPayAmount());

                            result.setPaymentResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalPayAmount(), outTotalPayAmount)));

                            if (Objects.equals(yzOrderDetail.getTotalPayAmount(), outOrderTotalPayAmount)) {
                                result.setPaymentResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalPayAmount(), outOrderTotalPayAmount)));
                                result.setOutPayment(outOrderTotalPayAmount);
                            } else {
                                result.setPaymentResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalAmount(), outTotalAmount)));
                                result.setOutPayment(outTotalPayAmount);
                            }

                            //优惠金额
                            result.setYzDiscountAmount(yzOrderDetail.getTotalDiscountAmount());

                            if (Objects.equals(yzOrderDetail.getTotalDiscountAmount(), outOrderTotalDiscountAmount)) {
                                result.setDiscountAmountResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalDiscountAmount(), outOrderTotalDiscountAmount)));
                                result.setOutDiscountAmount(outOrderTotalDiscountAmount);
                            } else {
                                result.setDiscountAmountResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalDiscountAmount(), outTotalDiscountAmount)));
                                result.setOutDiscountAmount(outTotalDiscountAmount);
                            }

                            String daogouResult = "true";
                            String guideCode = outOrderDetail.getGuideCode();
                            if (CollectionUtils.isNotEmpty(guideNos)) {
                                guideNos = guideNos.stream().distinct().collect(Collectors.toList());
                                result.setYzGuideNoList(String.join(",", guideNos));
                            }
                            if (StringUtils.isNotBlank(guideCode) && !Objects.equals(guideCode, ",")) {
                                String[] outGuideCodes = guideCode.split(",");
                                for (String outGuideCode : outGuideCodes) {
                                    String yzOpenId = null;
                                    String cacheKey = "kaileshi:guide_yz_open_id1:" + rootKdtId + ":" + outGuideCode;
                                    RBucket<String> bucket = redissonClient.getBucket(cacheKey);
                                    yzOpenId = bucket.get();

                                    if (StringUtils.isBlank(yzOpenId)) { // Cache Miss
                                        String yzOpenIdQueryStr = queryYzOpenId(outGuideCode);
                                        YouzanScrmCustomerDetailGetResult customerDetailGetResult = JSON.parseObject(yzOpenIdQueryStr, YouzanScrmCustomerDetailGetResult.class);
                                        String mobile2YzOpenId = outGuideCode;
                                        if (customerDetailGetResult.getSuccess() && Objects.nonNull(customerDetailGetResult.getData())) {
                                            mobile2YzOpenId = customerDetailGetResult.getData().getYzOpenId();
                                        }
                                        ShoppingGuideRelationDO shoppingGuideRelation = infraShoppingGuideRelationMapper.getBySellerId(rootKdtId, mobile2YzOpenId);
                                        if (Objects.nonNull(shoppingGuideRelation) && StringUtils.isNotBlank(shoppingGuideRelation.getYzOpenId())) {
                                            yzOpenId = shoppingGuideRelation.getYzOpenId();
                                            bucket.set(yzOpenId, 24, TimeUnit.HOURS); // Cache the found yzOpenId
                                        } else {
                                            //通过手机号查询一下有赞导购
                                            String yzGuideOpenIdStr = queryYzGuideOpenIdByMobile(outGuideCode);
                                            if (StringUtils.isNotBlank(yzGuideOpenIdStr)) {
                                                JSONObject jsonObject1 = JSON.parseObject(yzGuideOpenIdStr);
                                                JSONObject dataObj = jsonObject1.getJSONObject("data");
                                                if (Objects.nonNull(dataObj)) {
                                                    yzOpenId = dataObj.getString("yz_open_id");
                                                    bucket.set(yzOpenId, 24, TimeUnit.HOURS);
                                                } else {
                                                    // Cache the fact that it's not found to prevent repeated DB calls
                                                    bucket.set("", 24, TimeUnit.HOURS);
                                                    yzOpenId = ""; // Use empty string to represent not found
                                                }
                                            }
                                        }
                                    }
                                    if (StringUtils.isBlank(yzOpenId)) {
                                        daogouResult = "存在未映射导购";
                                        break;
                                    }
                                    if (!guideNos.contains(yzOpenId)) {
                                        daogouResult = "映射的导购id与订单中导购id不一致";
                                        break;
                                    }
                                }
                                result.setOutGuideNoList(guideCode);
                            }
                            result.setItemGuideResult(daogouResult);

                            kaiLeShiOrderAlignResultMapper.insert(result);

                            orderAlign.setStatus(STATUS_ALIGNED);
                            kaiLeShiOrderAlignMapper.update(orderAlign);

                        } catch (Exception e) {
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutTid(), e);
                        }
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("查询Detail任务失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        } finally {
            lock.unlock();
        }
        log.info("订单对账任务结束");
        return YzCloudResponse.success();
    }

    private String queryBzYzOpenId(String yzOpenId) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"fields\":\"user_base\",\"is_do_ext_point\":false,\"yz_open_id\":\"%s\"}", yzOpenId));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.scrm.customer.detail.get/1.0.1?access_token=8b03e0d8b062a2da758322b4a24a37c")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=92a8083254e69a13319c5b46cd8c54db382a5c7ff40aa5e976b0d9f6f8f7f0b4")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    private String queryYzGuideOpenIdByMobile(String outGuideCode) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\n    \"request\":{\n        \"mobile\":\"%s\"\n    }\n}", outGuideCode));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.guide.shoppingguide.get/2.0.0?access_token=8b03e0d8b062a2da758322b4a24a37c")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=ed13b12cd2861c0621c347c5a26b42b19c736213fa37cdb51cde5aebf9257ba3")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String parseEanCode(String eanCode, String productCode) {
        if (StringUtils.isEmpty(eanCode)) {
            return productCode;
        }
        String[] codes = eanCode.split(",");
        for (String code : codes) {
            if (code.startsWith("69")) {
                return code;
            }
        }
        return productCode;
    }

    public static String queryTid(String outTid) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\n    \"appId\":\"42243307_kylin\",\n    \"outTid\":\"%s\"\n}", outTid));
        Request request = new Request.Builder()
                .url("https://youzanyun-connector-kylin.isv.youzan.com/kaileshi/orderRelation/query")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "_kdt_id_=91004745; kdt_id=19075201; acw_tc=4b1b883359ecbd6d2ba882e8bfddff35c4fec41ece8df7917353cc60041e9907")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String queryDetail(String tid) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"tid\":\"%s\"}", tid));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.trade.get/4.0.2?access_token=8b03e0d8b062a2da758322b4a24a37c")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=064c13bee4b4da2a4c388a22d53d56e15eaacc2d04ef5b64685587cc076b0b4c")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String queryYzOpenId(String mobile) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"fields\":\"user_base\",\"is_do_ext_point\":false,\"account_info\":{\"account_id\":\"%s\",\"account_type\":2}}", mobile));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.scrm.customer.detail.get/1.0.1?access_token=8b03e0d8b062a2da758322b4a24a37c")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=92a8083254e69a13319c5b46cd8c54db382a5c7ff40aa5e976b0d9f6f8f7f0b4")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }


    public static String kylinOrderDetailQuery(String outTid) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";
        String url = String.format("%s?memberType=kailas&orderBeginTime=%s&orderEndTime=%s&pageNo=1&pageSize=20&orderId=%s",
                API_URL, "2010-11-18 03:00:00".replace(" ", "%20"), "2026-11-18 04:00:00".replace(" ", "%20"), outTid);

        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/order/page"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();

        try {
            Response response = client.newCall(request).execute();
            String responseStr = response.body().string();
            return responseStr;
        } catch (IOException e) {
            log.error("数云订单查询超时,tid:{}", outTid, e);
            throw new RuntimeException(e);
        }
    }

    public static String queryEanCode(String itemNo, String skuNo) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";
        String url = String.format("%s?memberType=kailas&createBeginTime=%s&createEndTime=%s&pageNo=1&pageSize=200&sqId=%s&productCode=%s",
                ITEM_API_URL, "2010-01-18 00:00:00".replace(" ", "%20"), "2026-12-12 00:00:00".replace(" ", "%20"), itemNo, skuNo);

        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/product/list"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        JSONObject jsonObject = JSON.parseObject(responseStr);
        if (Objects.nonNull(jsonObject) && jsonObject.getJSONArray("data").size() > 0) {
            return jsonObject.getJSONArray("data").getJSONObject(0).getString("eanCode");
        }
        return "";
    }

    private void handleOrderItemType(boolean isExchangeOrder, String orderId, String originOrderId, KaileshiOrderQuerySubItemResponseDTO orderItem, List<KaileshiOrderQuerySubItemResponseDTO> exchangeOrderItems, List<KaileshiOrderQuerySubItemResponseDTO> refundOrderItems, List<KaileshiOrderQuerySubItemResponseDTO> noSourceRefundOrderItems, List<KaileshiOrderQuerySubItemResponseDTO> normalOrderItems) {
        //无原单号时，可能为非会员订单换货，可能为正常订单
        if (StringUtils.isBlank(originOrderId)) {
            //tips:非会员订单换货，是没有原单号的
            if (isExchangeOrder) {
                //换货
                if (orderItem.getQuantity() > 0) {
                    //换货
                    exchangeOrderItems.add(orderItem);
                } else if (orderItem.getQuantity() < 0) {
                    //无原单退款
                    noSourceRefundOrderItems.add(orderItem);
                }
            } else {
                normalOrderItems.add(orderItem);
            }
        } else {
            if (orderItem.getQuantity() > 0) {
                //换货
                exchangeOrderItems.add(orderItem);
            } else if (orderItem.getQuantity() < 0) {
                //退款单号与原单一致，说明是无原单退款
                if (Objects.equals(originOrderId, orderId)) {
                    //无原单退款
                    noSourceRefundOrderItems.add(orderItem);
                } else {
                    //有原单退款
                    refundOrderItems.add(orderItem);
                }
            }
        }
    }

    public static String memberQuery(String mobile) throws IOException {
        if (mobile.startsWith("+")) {
            mobile = mobile.replace("+", "%2B");
        }
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";

        String url = String.format("%s?memberType=kailas&memberId=%s&pageNo=1&pageSize=50",
                API_URL, mobile);

        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/getMemberInfo"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    private String memberChannelQuery(String memberId) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";
        String url = String.format("%s?memberType=kailas&memberId=%s", API_CHANNEL_URL, memberId);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/query"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }


}