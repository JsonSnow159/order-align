package com.example.orderalign.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.example.orderalign.dto.OrderAlignDTO;
import com.example.orderalign.dto.OutOrderDetail;
import com.example.orderalign.dto.YzOrderDetail;
import com.example.orderalign.dto.kylin.*;
import com.example.orderalign.mapper.*;
import com.example.orderalign.model.*;
import com.example.orderalign.utils.KaileshiUtil;
import com.example.orderalign.utils.SignUtil;
import com.youzan.cloud.connector.sdk.client.YzCloudResponse;
import com.youzan.cloud.connector.sdk.common.exception.RecoverableException;
import com.youzan.cloud.connector.sdk.common.exception.UnrecoverableException;
import com.youzan.cloud.connector.sdk.common.utils.DateFormatUtil;
import com.youzan.cloud.connector.sdk.common.utils.MoneyUtil;

import com.youzan.cloud.connector.sdk.infra.dal.entity.OrderRefundRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.ShopRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.SubOrderRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.entity.UserRelationDO;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraSubOrderRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraUserRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.OrderRefundRelationMapper;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.ShopRelationMapper;
import com.youzan.cloud.open.sdk.gen.v3_0_0.model.YouzanTradeRefundGetResult;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/kaileshi/refund")
public class KaiLeShiRefundOrderAlignController {
    @Resource
    private KaiLeShiOrderAlignMapper kaiLeShiOrderAlignMapper;
    @Resource
    private KaiLeShiOrderRefundAlignMapper kaiLeShiOrderRefundAlignMapper;
    @Resource
    private OrderRefundRelationMapper orderRefundRelationMapper;
    @Resource
    private YouzanOrderDetailMapper youzanOrderDetailMapper;
    @Resource
    private ThirdPartyOrderDetailMapper thirdPartyOrderDetailMapper;
    @Resource
    private InfraSubOrderRelationMapper infraSubOrderRelationMapper;
    @Resource
    private KaiLeShiRefundOrderAlignResultMapper kaiLeShiRefundOrderAlignResultMapper;
    @Resource
    private KaiLeShiNoSourceRefundOrderAlignResultMapper kaiLeShiNoSourceRefundOrderAlignResultMapper;
    @Resource
    private ShopRelationMapper shopRelationMapper;
    private static final String ITEM_API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/product/list";
    @Resource
    private InfraUserRelationMapper infraUserRelationMapper;
    @Resource
    private RedissonClient redissonClient;
    private static final String REFUND_API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/refundOrder/page";
    static OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)    // 连接超时
            .readTimeout(3, TimeUnit.SECONDS)       // 读取超时
            .writeTimeout(3, TimeUnit.SECONDS)      // 写入超时
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
    private static final int STATUS_ALIGNED = 5;
    private static final int BATCH_SIZE = 100;

    private static final int DETAIL_STATUS_QUERIED = 1;
    private static final int DETAIL_STATUS_YZ_FAIL = 3;
    private static final int DETAIL_STATUS_OUT_FAIL = 4;


    @PostMapping("/updateRefundOrder")
    public YzCloudResponse<Object> updateRefundOrder(@RequestBody OrderAlignDTO param) {
        log.info("凯乐石退单对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> outRefundIds = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getOutTid())) {
                outRefundIds.add(param.getOutTid());
            }
            if (CollectionUtils.isNotEmpty(param.getOutTidList())) {
                outRefundIds.addAll(param.getOutTidList());
            }

            if (CollectionUtils.isEmpty(outRefundIds)) {
                return YzCloudResponse.success("outRefundId is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String outRefundId : outRefundIds) {
                KaiLeShiOrderRefundAlign existingLog = kaiLeShiOrderRefundAlignMapper.selectByAppIdAndOutRefundId(appId, outRefundId);
                if (Objects.nonNull(existingLog)) {
                    log.warn("outRefundId: {} already exists, skipping.", outRefundId);
                    continue;
                }

                KaiLeShiOrderRefundAlign tradePushLog = new KaiLeShiOrderRefundAlign();
                tradePushLog.setAppId(appId);
                tradePushLog.setKdtId(rootKdtId);
                tradePushLog.setOutRefundId(outRefundId);
                tradePushLog.setRefundId(""); // refundId is not provided in this scenario
                tradePushLog.setStatus(STATUS_PENDING);
                kaiLeShiOrderRefundAlignMapper.insert(tradePushLog);
                log.info("outRefundId: {} inserted for processing.", outRefundId);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

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
            List<KaiLeShiOrderRefundAlign> pendingOrders = kaiLeShiOrderRefundAlignMapper.selectByStatusWithLimit(appId, STATUS_PENDING, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String outRefundId = orderAlign.getOutRefundId();

                            ThirdPartyOrderDetail thirdPartyOrderDetail = new ThirdPartyOrderDetail();
                            thirdPartyOrderDetail.setAppId(appId);
                            thirdPartyOrderDetail.setKdtId(rootKdtId);
                            thirdPartyOrderDetail.setOutTid(outRefundId);

                            KaileshiRefundOrderQueryResponseDTO kaileshiOrderQueryResponse = new KaileshiRefundOrderQueryResponseDTO();
                            String kylinOrderDetailStr = this.kylinRefundOrderDetailQuery(outRefundId);
                            if (StringUtils.isNotBlank(kylinOrderDetailStr)) {
                                kaileshiOrderQueryResponse = JSON.parseObject(JSON.toJSONString(JSON.parseObject(kylinOrderDetailStr).getJSONArray("data").getJSONObject(0)), KaileshiRefundOrderQueryResponseDTO.class);
                            }
                            if (Objects.isNull(kaileshiOrderQueryResponse)) {
                                thirdPartyOrderDetail.setStatus(DETAIL_STATUS_OUT_FAIL);
                                thirdPartyOrderDetailMapper.insert(thirdPartyOrderDetail);
                                orderAlign.setStatus(STATUS_NOT_FOUND);
                                kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                                log.warn("查询数云订单详情失败, outTid: {}", outRefundId);
                                return;
                            }

                            List<KaileshiOrderRefundQuerySubItemResponseDTO> refundOrderItems = kaileshiOrderQueryResponse.getRefundOrderItems();
                            List<KaileshiOrderRefundQuerySubItemResponseDTO> noSourceRefundOrderItems = new ArrayList<>();
                            List<KaileshiOrderRefundQuerySubItemResponseDTO> exchangeOrderItems = new ArrayList<>();
                            List<KaileshiOrderRefundQuerySubItemResponseDTO> normalRefundOrderItems = new ArrayList<>();
                            //筛选哪些是正向、哪些是退款，拆分退款oids
                            for (KaileshiOrderRefundQuerySubItemResponseDTO refundOrderItem : refundOrderItems) {
                                String originOrderId = refundOrderItem.getOriginOrderId();
                                handleOrderItemType(outRefundId, originOrderId, refundOrderItem, noSourceRefundOrderItems, exchangeOrderItems, normalRefundOrderItems);
                            }
                            orderAlign.setIsNoSourceRefundOrder(0);
                            //无原单退单
                            if (CollectionUtils.isNotEmpty(noSourceRefundOrderItems)) {
                                //无原单退款单
                                orderAlign.setOutRefundId("Ex" + outRefundId);
                                orderAlign.setType("正常退单");
                                orderAlign.setIsNoSourceRefundOrder(1);
                            }

                            if (CollectionUtils.isNotEmpty(exchangeOrderItems)) {
                                KaiLeShiOrderAlign tradePushLog = new KaiLeShiOrderAlign();
                                tradePushLog.setAppId(appId);
                                tradePushLog.setKdtId(rootKdtId);
                                tradePushLog.setOutTid(outRefundId);
                                tradePushLog.setTid(""); // tid is not provided in this scenario
                                tradePushLog.setStatus(STATUS_OUT_DETAIL_QUERIED);
                                tradePushLog.setType("逆向订单");
                                kaiLeShiOrderAlignMapper.insert(tradePushLog);
                            }

                            OutOrderDetail outOrderDetail = new OutOrderDetail();
                            outOrderDetail.setChannel(kaileshiOrderQueryResponse.getChannelType());
                            outOrderDetail.setOutTid(outRefundId);
                            outOrderDetail.setCustomerNo(kaileshiOrderQueryResponse.getCustomerNo());
                            outOrderDetail.setMemberId(kaileshiOrderQueryResponse.getMemberId());
                            outOrderDetail.setShopCode(kaileshiOrderQueryResponse.getShopCode());
                            outOrderDetail.setTotalAmount(MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getRefundFee()));
                            outOrderDetail.setTotalPayAmount(MoneyUtil.Yuan2Cent(kaileshiOrderQueryResponse.getRefundFee()));
                            outOrderDetail.setCreateTime(kaileshiOrderQueryResponse.getRefundTime());
                            outOrderDetail.setPayTime(kaileshiOrderQueryResponse.getRefundTime());

                            List<OutOrderDetail.SubOrder> oidList = new ArrayList<>();
                            List<KaileshiOrderRefundQuerySubItemResponseDTO> orderItems = kaileshiOrderQueryResponse.getRefundOrderItems();
                            for (KaileshiOrderRefundQuerySubItemResponseDTO orderItem : orderItems) {
                                OutOrderDetail.SubOrder subOrder = new OutOrderDetail.SubOrder();
                                subOrder.setNum(orderItem.getQuantity());
                                subOrder.setTotalFee(MoneyUtil.Yuan2Cent(orderItem.getRefundFee()));
                                subOrder.setPayment(MoneyUtil.Yuan2Cent(orderItem.getRefundFee()));
                                subOrder.setItemNo(orderItem.getProductCode());
                                subOrder.setSkuNo(orderItem.getSkuId());
                                subOrder.setTitle(orderItem.getProductName());
                                subOrder.setOutOid(orderItem.getOrderItemId());
                                subOrder.setOutOriginOrderItemIdId(orderItem.getOriginOrderItemIdId());
                                oidList.add(subOrder);
                            }
                            outOrderDetail.setOidList(oidList);
                            thirdPartyOrderDetail.setOutTidDetail(JSON.toJSONString(outOrderDetail));
                            thirdPartyOrderDetail.setStatus(DETAIL_STATUS_QUERIED);

                            thirdPartyOrderDetailMapper.insert(thirdPartyOrderDetail);
                            //三方详情已查询
                            orderAlign.setStatus(STATUS_OUT_DETAIL_QUERIED);
                            orderAlign.setType("正常退单");
                            kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutRefundId(), e);
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
    @PostMapping("/queryRefundId")
    public YzCloudResponse<Object> queryTid(@RequestBody OrderAlignDTO param) {
        log.info("开始查询退单refundId,appId:{}", param.getAppId());
        String lockKey = String.format("queryRefundId_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},订单映射对齐正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new UnrecoverableException("获取锁失败");
        }

        try {
            String appId = param.getAppId();
            List<KaiLeShiOrderRefundAlign> pendingOrders = kaiLeShiOrderRefundAlignMapper.selectByStatusWithLimit(appId, STATUS_OUT_DETAIL_QUERIED, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            OrderRefundRelationDO orderRefundRelation = orderRefundRelationMapper.getByOutRefundId(orderAlign.getAppId(), orderAlign.getKdtId(), orderAlign.getOutRefundId());
                            if (Objects.nonNull(orderRefundRelation) && StringUtils.isNotBlank(orderRefundRelation.getRefundId())) {
                                orderAlign.setStatus(STATUS_FOUND); // Found
                                orderAlign.setRefundId(orderRefundRelation.getRefundId());
                                log.info("outRefundId: {} 找到 refundId: {}", orderAlign.getOutRefundId(), orderRefundRelation.getRefundId());
                            } else {
                                String refundId = queryRefundId(orderAlign.getOutRefundId());
                                if (StringUtils.isNotBlank(refundId)) {
                                    orderAlign.setStatus(STATUS_FOUND); // Found
                                    orderAlign.setRefundId(refundId);
                                } else {
                                    orderAlign.setStatus(2); // Not found
                                    log.warn("outRefundId: {} 未找到 tid", orderAlign.getOutRefundId());
                                }
                            }
                            kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outTid: {}", orderAlign.getOutRefundId(), e);
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
            List<KaiLeShiOrderRefundAlign> pendingOrders = kaiLeShiOrderRefundAlignMapper.selectByStatusWithLimit(appId, STATUS_FOUND, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的订单");
                return YzCloudResponse.success();
            }

            log.info("本批次处理订单数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String refundId = orderAlign.getRefundId();

                            YouzanOrderDetail youzanOrderDetail = new YouzanOrderDetail();
                            youzanOrderDetail.setAppId(appId);
                            youzanOrderDetail.setKdtId(rootKdtId);
                            youzanOrderDetail.setTid(refundId);

                            if (StringUtils.isNotBlank(refundId) && !Objects.equals(orderAlign.getIsNoSourceRefundOrder(), 1)) {
                                String refundDetailStr = queryRefundDetail(refundId);
                                YouzanTradeRefundGetResult youzanTradeRefundGetResult = JSON.parseObject(refundDetailStr, YouzanTradeRefundGetResult.class);
                                if (!youzanTradeRefundGetResult.getSuccess() || Objects.isNull(youzanTradeRefundGetResult.getData())) {
                                    orderAlign.setStatus(STATUS_NOT_FOUND);
                                    kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                                    log.warn("查询有赞订单详情失败, refundId: {}", refundId);
                                    return;
                                }
                                YouzanTradeRefundGetResult.YouzanTradeRefundGetResultData data = youzanTradeRefundGetResult.getData();
                                YzOrderDetail yzOrderDetail = new YzOrderDetail();
                                String tid = data.getTid();
                                yzOrderDetail.setTid(data.getRefundId());
                                yzOrderDetail.setKdtId(data.getKdtId());
                                yzOrderDetail.setCreateTime(data.getCreated());
                                yzOrderDetail.setTotalAmount(MoneyUtil.YuanStr2Cent(data.getRefundFee()));
                                yzOrderDetail.setTotalPayAmount(MoneyUtil.YuanStr2Cent(data.getRefundFee()));
                                List<YzOrderDetail.SubOrder> yzOidList = new ArrayList<>();
                                List<YouzanTradeRefundGetResult.YouzanTradeRefundGetResultRefundorderitem> refundOrderItems = data.getRefundOrderItem();

                                for (YouzanTradeRefundGetResult.YouzanTradeRefundGetResultRefundorderitem refundOrderItem : refundOrderItems) {
                                    Long refundFee = refundOrderItem.getRefundFee();
                                    String oid = refundOrderItem.getOid();
                                    SubOrderRelationDO subOrderRelation = infraSubOrderRelationMapper.getByYz(appId, tid, oid);
                                    if (Objects.isNull(subOrderRelation)) {
                                        return;
                                    }
                                    String outOid = subOrderRelation.getOutOid();
                                    YzOrderDetail.SubOrder subOrder = new YzOrderDetail.SubOrder();
                                    subOrder.setOutOid(outOid);
                                    subOrder.setTotalAmount(refundFee);
                                    subOrder.setPayment(refundFee);
                                    subOrder.setNum(refundOrderItem.getItemNum());
                                    yzOidList.add(subOrder);
                                }
                                yzOrderDetail.setOidList(yzOidList);
                                youzanOrderDetail.setTidDetail(JSON.toJSONString(yzOrderDetail));
                                youzanOrderDetail.setStatus(DETAIL_STATUS_QUERIED);
                                youzanOrderDetailMapper.insert(youzanOrderDetail);
                            }

                            orderAlign.setStatus(STATUS_DETAIL_QUERIED);
                            kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outRefundId: {}", orderAlign.getOutRefundId(), e);
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
        log.info("开始退单详情对齐");
        String appId = param.getAppId();
        String lockKey = String.format("refundDetailAlign_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},退单详情对齐正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }
        try {
            List<KaiLeShiOrderRefundAlign> pendingOrders = kaiLeShiOrderRefundAlignMapper.selectByStatusWithLimit(appId, STATUS_DETAIL_QUERIED, BATCH_SIZE);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的退单");
                return YzCloudResponse.success();
            }
            String[] appIdArr = appId.split("_");
            String tripartite = appIdArr[1];
            Long rootKdtId = param.getRootKdtId();
//            Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);
            List<String> pendingTids = pendingOrders.stream().map(KaiLeShiOrderRefundAlign::getOutRefundId).collect(Collectors.toList());
            log.info("本批次处理订单数量: {}, 订单号: {}", pendingOrders.size(), JSON.toJSONString(pendingTids));

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(orderAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String refundId = orderAlign.getRefundId();
                            String outRefundId = orderAlign.getOutRefundId();
                            Integer isNoSourceRefundOrder = orderAlign.getIsNoSourceRefundOrder();
                            String type = orderAlign.getType();
                            log.info("开始处理单号:{}", outRefundId);
                            if (Objects.equals(isNoSourceRefundOrder, 1)) {
                                //TODO NoSourceAlign
                                KaiLeShiNoSourceRefundOrderAlignResult kaiLeShiNoSourceRefundOrderAlignResult = kaiLeShiNoSourceRefundOrderAlignResultMapper.selectByRefundId(appId, refundId);
                                if (Objects.nonNull(kaiLeShiNoSourceRefundOrderAlignResult)) {
                                    Long id = kaiLeShiNoSourceRefundOrderAlignResult.getId();
                                    kaiLeShiNoSourceRefundOrderAlignResultMapper.deleteByPrimaryKey(id);
                                }

                                YouzanOrderDetail youzanOrderDetail = youzanOrderDetailMapper.selectByTid(refundId);
                                ThirdPartyOrderDetail thirdPartyOrderDetail = thirdPartyOrderDetailMapper.selectByOutTid(outRefundId);

                                if (Objects.isNull(youzanOrderDetail) || StringUtils.isBlank(youzanOrderDetail.getTidDetail())) {
                                    log.error("有赞订单详情不存在, refundId: {}", refundId);
                                    return;
                                }
                                if (Objects.isNull(thirdPartyOrderDetail) || StringUtils.isBlank(thirdPartyOrderDetail.getOutTidDetail())) {
                                    log.error("三方订单详情不存在, outRefundId: {}", outRefundId);
                                    return;
                                }

                                YzOrderDetail yzOrderDetail = JSON.parseObject(youzanOrderDetail.getTidDetail(), YzOrderDetail.class);
                                OutOrderDetail outOrderDetail = JSON.parseObject(thirdPartyOrderDetail.getOutTidDetail(), OutOrderDetail.class);

                                KaiLeShiNoSourceRefundOrderAlignResult result = new KaiLeShiNoSourceRefundOrderAlignResult();

                                result.setKdtId(rootKdtId);
                                result.setAppId(appId);
                                result.setRefundId(refundId);
                                result.setOutRefundId(outRefundId);

                                // Member alignment
                                result.setUserId(yzOrderDetail.getUserId());
                                String yzOpenId = userId2YzOpenId(yzOrderDetail.getUserId());
                                result.setYzMemberId(yzOrderDetail.getYzOpenId());
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
                                        result.setMemberIdResult(String.valueOf(Objects.equals(outOpenId, result.getOutMemberId())));
                                    }
                                } else {
                                    result.setMemberIdResult("会员映射不存在");
                                }

                                // Shop alignment
                                Long kdtId = yzOrderDetail.getKdtId();
                                result.setNodeKdtId(kdtId);
                                List<ShopRelationDO> shopRelationList = shopRelationMapper.getByBranchId(appId, kdtId, "UP");
                                if (CollectionUtils.isNotEmpty(shopRelationList)) {
                                    result.setYzShopNo(shopRelationList.get(0).getOutBranchId());
                                    result.setOutShopNo(outOrderDetail.getShopCode());
                                    result.setShopResult(String.valueOf(Objects.equals(result.getYzShopNo(), result.getOutShopNo())));
                                } else {
                                    result.setShopResult("店铺未映射");
                                }
                                result.setIsMockItemId("false");
                                List<YzOrderDetail.SubOrder> oidList = yzOrderDetail.getOidList();
                                for (YzOrderDetail.SubOrder yzOid : oidList) {
                                    Long itemId = yzOid.getItemId();
                                    Long skuId = yzOid.getSkuId();
                                    if (itemId == 1 || skuId == 1) {
                                        result.setIsMockItemId("true");
                                        break;
                                    }
                                }
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
                                Long outRefundTotalFee = 0L;
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
                                        org.redisson.api.RBucket<String> skuCodeBucket = redissonClient.getBucket(outSkuNo);
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
                                    Long outRefundFee = Math.abs(outOrder.getPayment());
                                    outRefundTotalFee += outRefundFee;
                                    outNum = Math.abs(outNum);

                                    boolean itemNoAlign = true;
                                    boolean itemNumAlign = true;
                                    boolean itemPaymentAlign = true;
//                        boolean guideAlign = true;

                                    for (YzOrderDetail.SubOrder yzOid : oidList) {
                                        String outOid = yzOid.getOutOid();
                                        if (Objects.equals(yzOutOid, outOid)) {
                                            String yzItemNo = yzOid.getItemNo();
                                            String yzSkuNo = yzOid.getSkuNo();
                                            if (StringUtils.isBlank(outSkuNo) && StringUtils.isNotBlank(yzSkuNo)) {
                                                itemNoAlign = false;
                                            } else if (!(Objects.equals(outItemNo, yzItemNo) && Objects.equals(outSkuNo, yzSkuNo))) {
                                                itemNoAlign = false;
                                            }

                                            Integer yzNum = yzOid.getNum();
                                            if (!outNum.equals(yzNum)) {
                                                itemNumAlign = false;
                                            }

                                            Long payment = yzOid.getPayment();
                                            if (!Objects.equals(payment, outRefundFee)) {
                                                itemPaymentAlign = false;
                                            }
                                            if (!itemNoAlign || !itemNumAlign || !itemPaymentAlign) {
                                                sb.append(outOid);
                                                if (!itemNoAlign) {
                                                    sb.append("商品或规格编码不一致;");
                                                    sb.append("有赞商品编码:" + yzItemNo + ";");
                                                    sb.append("数云商品编码:" + outItemNo + ";");
                                                    sb.append("有赞规格编码:" + yzSkuNo + ";");
                                                    sb.append("数云规格编码:" + outSkuNo + ";");
                                                }
                                                if (!itemNumAlign) {
                                                    sb.append("下单数量不一致;");
                                                }
                                                if (!itemPaymentAlign) {
                                                    sb.append("商品实付总额不一致;");
                                                }
                                            }
                                        }
                                    }
                                    if (!itemNoAlign || !itemNumAlign || !itemPaymentAlign) {
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
                                result.setYzRefundFee(yzOrderDetail.getTotalPayAmount());
                                result.setOutRefundFee(outRefundTotalFee);
                                result.setRefundFeeResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalPayAmount(), outRefundTotalFee)));

                                kaiLeShiNoSourceRefundOrderAlignResultMapper.insert(result);

                                orderAlign.setStatus(STATUS_ALIGNED);
                                kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                            } else {

                                KaiLeShiRefundOrderAlignResult kaiLeShiRefundOrderAlignResult = kaiLeShiRefundOrderAlignResultMapper.selectByRefundId(appId, refundId);
                                if (Objects.nonNull(kaiLeShiRefundOrderAlignResult)) {
                                    Long id = kaiLeShiRefundOrderAlignResult.getId();
                                    kaiLeShiRefundOrderAlignResultMapper.deleteByPrimaryKey(id);
                                }
                                YouzanOrderDetail youzanOrderDetail = youzanOrderDetailMapper.selectByTid(refundId);
                                ThirdPartyOrderDetail thirdPartyOrderDetail = thirdPartyOrderDetailMapper.selectByOutTid(outRefundId);

                                if (Objects.isNull(youzanOrderDetail) || StringUtils.isBlank(youzanOrderDetail.getTidDetail())) {
                                    log.error("有赞订单详情不存在, refundId: {}", refundId);
                                    return;
                                }
                                if (Objects.isNull(thirdPartyOrderDetail) || StringUtils.isBlank(thirdPartyOrderDetail.getOutTidDetail())) {
                                    log.error("三方订单详情不存在, outRefundId: {}", outRefundId);
                                    return;
                                }

                                YzOrderDetail yzOrderDetail = JSON.parseObject(youzanOrderDetail.getTidDetail(), YzOrderDetail.class);
                                OutOrderDetail outOrderDetail = JSON.parseObject(thirdPartyOrderDetail.getOutTidDetail(), OutOrderDetail.class);

                                KaiLeShiRefundOrderAlignResult result = new KaiLeShiRefundOrderAlignResult();

                                result.setKdtId(rootKdtId);
                                result.setAppId(appId);
                                result.setRefundId(refundId);
                                result.setOutRefundId(outRefundId);

                                //创建时间对齐
                                result.setYzCreateTime(yzOrderDetail.getCreateTime());
                                result.setOutCreateTime(outOrderDetail.getCreateTime());

                                Date createDate = KaileshiUtil.convertTime2UTC8DateUtil(outOrderDetail.getCreateTime());
                                String createStr = DateFormatUtil.parseDate2Str(createDate);
                                result.setCreateTimeResult(String.valueOf(Objects.equals(yzOrderDetail.getCreateTime(), createStr)));

                                //实退金额
                                result.setYzPayment(yzOrderDetail.getTotalPayAmount());
                                result.setOutPayment(outOrderDetail.getTotalPayAmount());
                                result.setPaymentResult(String.valueOf(Objects.equals(yzOrderDetail.getTotalPayAmount(), outOrderDetail.getTotalPayAmount())));

                                // Shop alignment
                                Long kdtId = yzOrderDetail.getKdtId();
                                result.setNodeKdtId(kdtId);
                                List<ShopRelationDO> shopRelationList = shopRelationMapper.getByBranchId(appId, kdtId, "UP");
                                if (CollectionUtils.isNotEmpty(shopRelationList)) {
                                    result.setYzShopNo(shopRelationList.get(0).getOutBranchId());
                                    result.setOutShopNo(outOrderDetail.getShopCode());
                                    result.setShopResult(String.valueOf(Objects.equals(result.getYzShopNo(), result.getOutShopNo())));
                                } else {
                                    result.setShopResult("店铺未映射");
                                }
                                List<OutOrderDetail.SubOrder> outOidList = outOrderDetail.getOidList();
                                boolean itemAlign = true;
                                StringBuilder sb = new StringBuilder("");

                                List<YzOrderDetail.SubOrder> oidList = yzOrderDetail.getOidList();
                                for (OutOrderDetail.SubOrder outOrder : outOidList) {
                                    Integer outNum = outOrder.getNum();
                                    //按订单类型来区分取值逻辑
                                    if (Objects.equals(type, "正常退单")) {
                                        if (outNum <= 0) {
                                            continue;
                                        }
                                    } else {
                                        if (outNum >= 0) {
                                            continue;
                                        }
                                    }

                                    String yzOutOid = outOrder.getOutOriginOrderItemIdId();

                                    Long outRefundFee = Math.abs(outOrder.getPayment());
                                    outNum = Math.abs(outNum);

                                    boolean itemNumAlign = true;
                                    boolean itemPaymentAlign = true;

                                    for (YzOrderDetail.SubOrder yzOid : oidList) {
                                        String outOid = yzOid.getOutOid();
                                        if (Objects.equals(yzOutOid, outOid)) {

                                            Integer yzNum = yzOid.getNum();
                                            if (!outNum.equals(yzNum)) {
                                                itemNumAlign = false;
                                            }

                                            Long payment = yzOid.getPayment();
                                            if (!Objects.equals(payment, outRefundFee)) {
                                                itemPaymentAlign = false;
                                            }
                                            if (!itemNumAlign || !itemPaymentAlign) {
                                                sb.append(outOid);
                                                if (!itemNumAlign) {
                                                    sb.append("退款数量不一致;");
                                                }
                                                if (!itemPaymentAlign) {
                                                    sb.append("商品实付总额不一致;");
                                                }
                                            }
                                        }
                                    }
                                    if (!itemNumAlign || !itemPaymentAlign) {
                                        itemAlign = false;
                                    }
                                }
                                if (!itemAlign) {
                                    result.setSubOrderResult("子订单不一致");
                                    result.setSubOrderFailReason(sb.toString());
                                } else {
                                    result.setSubOrderResult("子订单一致");
                                }
                                kaiLeShiRefundOrderAlignResultMapper.insert(result);

                                orderAlign.setStatus(STATUS_ALIGNED);
                                kaiLeShiOrderRefundAlignMapper.update(orderAlign);
                            }
                        } catch (Exception e) {
                            log.error("处理单个订单失败 outRefundId: {}", orderAlign.getOutRefundId(), e);
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


    public static String kylinRefundOrderDetailQuery(String outTid) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";

        String url = String.format("%s?memberType=kailas&orderBeginTime=%s&orderEndTime=%s&pageNo=1&pageSize=20&orderId=%s",
                REFUND_API_URL, "2010-01-18 00:00:00".replace(" ", "%20"), "2025-12-12 00:00:00".replace(" ", "%20"), outTid);

        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/refundOrder/page"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    private void handleOrderItemType(String refundId, String originOrderId, KaileshiOrderRefundQuerySubItemResponseDTO refundOrderItem, List<KaileshiOrderRefundQuerySubItemResponseDTO> noSourceRefundOrderItems, List<KaileshiOrderRefundQuerySubItemResponseDTO> exchangeOrderItems, List<KaileshiOrderRefundQuerySubItemResponseDTO> normalRefundOrderItems) {
        //原单信息为空，可能是无原单退款
        if (StringUtils.isBlank(originOrderId)) {
            throw new UnrecoverableException("原单信息为空，无法判断订单类型");
        } else {
            //退款单号与原单一致，说明是无原单退款
            if (refundOrderItem.getQuantity() < 0) {
                //换货
                exchangeOrderItems.add(refundOrderItem);
            } else {
                if (Objects.equals(originOrderId, refundId)) {
                    //无原单退款
                    noSourceRefundOrderItems.add(refundOrderItem);
                } else {
                    //正常退款单
                    normalRefundOrderItems.add(refundOrderItem);
                }
            }
        }
    }

    public static String queryRefundId(String outTid) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\n    \"appId\":\"42243307_kylin\",\"rootKdtId\":42243307,    \"outRefundId\":\"%s\"\n}", outTid));
        Request request = new Request.Builder()
                .url("https://youzanyun-connector-kylin.isv.youzan.com/kaileshi/refundRelation/query")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "_kdt_id_=91004745; kdt_id=19075201; acw_tc=4b1b883359ecbd6d2ba882e8bfddff35c4fec41ece8df7917353cc60041e9907")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String queryRefundDetail(String refundId) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"refund_id\":\"%s\"}", refundId));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.trade.refund.get/3.0.0?access_token=9148af1d905f09244aafcb298954af3")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=064c13bee4b4da2a4c388a22d53d56e15eaacc2d04ef5b64685587cc076b0b4c")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String userId2YzOpenId(String userId) throws IOException {
        MediaType mediaType = MediaType.parse("application/json;charset=UTF-8");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"serviceName\":\"com.youzan.uic.open.service.api.service.OpenIdService\",\"methodName\":\"getOrCreateIdMappingByUserId\",\"args\":[{\"userId\":\"%s\"}]}", userId));
        Request request = new Request.Builder()
                .url("https://gateway.qima-inc.com/api/bypass/dubbo/-/com.youzan.uic.open.service.api.service.OpenIdService/-/getOrCreateIdMappingByUserId")
                .method("POST", body)
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
                .addHeader("Connection", "keep-alive")
                .addHeader("Content-Type", "application/json;charset=UTF-8")
                .addHeader("Origin", "https://gateway.qima-inc.com")
                .addHeader("Referer", "https://gateway.qima-inc.com/common-tools/id-conver")
                .addHeader("Sec-Fetch-Dest", "empty")
                .addHeader("Sec-Fetch-Mode", "cors")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36")
                .addHeader("rpc-persist-life-test-data-access", "all")
                .addHeader("sec-ch-ua", "\"Google Chrome\";v=\"141\", \"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"141\"")
                .addHeader("sec-ch-ua-mobile", "?0")
                .addHeader("sec-ch-ua-platform", "\"macOS\"")
                .addHeader("Cookie", "yz_log_uuid=cd47abfa-a8d9-bd3d-b787-4b68162e1a48; yz_log_ftime=1748412995642; KDTSESSIONID=YZ1417896281570824192YZDcuBETVr; loc_dfp=46be6a08ebd2f09069c29adf23b9a2ee; dfp=5d704e6fe94973f0ea5728c6c30c5c85; cas_username=cloud_wujincai; access_user=13259_1; cas=8ea6db75a584cc82749627f62e74015146c9c5799b3ba17a4b228bb80f725db1457; gateway-front-prod.sid=6QKEVNmuWfuwXVAjyczNrukHcktLNFIOLc2JwfVRbw+S2vLPUXPNc3PJVOaqiU8AQ957n73qT9Zzh62LOvOCShb7SMuGVkKvqpk4ueYBlKT7nUVNHy+sjZQ28ASHH/sRnJwblLvsc9uBmobJd8pOcWOo615IyNcuij0hu7SnFfa5lzR8h+F1IuxXFU85JHWnbnl+SjMelwC+xXMZgK/K7w==; garden-front.sid=prEOuOGFEAOs/FI8KSBPZ8S9y5WKedy/m4mtIRY5DNDuKJG005e53Ft6y5Hgkl/K8I7d5YQf/xQu+lbCxXC22oOCGCcajJSUQ5BvKnZDR0808dhXOJu5IL31AiFaJJY++h2dBZn1lgmMGu8j4b5zFUQrE0/WYjX4PPeRfZQKxnPZuQtTrIt3YGLkBsZ3exHMdk79Qx7unXpW2n5ZlLeMVw==; garden-front.sid.sig=UlvoqVPNTSUJZ_Xu8-jSbD36uB8; yz_log_seqn=1; TSID=e173ae37cc1b429a86d161f8b44ff922; yz_log_seqb=1760342309592; yz_log_seqn=2")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        JSONObject jsonObject = JSON.parseObject(responseStr);
        if (Objects.nonNull(jsonObject) && Objects.nonNull(jsonObject.getJSONObject("data"))) {
            return jsonObject.getJSONObject("data").getString("openId");
        }
        return "";
    }


    public static String queryEanCode(String itemNo, String skuNo) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";
        String url = String.format("%s?memberType=kailas&createBeginTime=%s&createEndTime=%s&pageNo=1&pageSize=200&sqId=%s&productCode=%s",
                ITEM_API_URL, "2010-01-18 00:00:00".replace(" ", "%20"), "2025-12-12 00:00:00".replace(" ", "%20"), itemNo, skuNo);

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
}
