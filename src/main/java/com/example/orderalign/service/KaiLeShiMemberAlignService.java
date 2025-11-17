package com.example.orderalign.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.example.orderalign.dto.member.KLSCustomMemberChannelQueryResponse;
import com.example.orderalign.dto.member.OutMemberDetail;
import com.example.orderalign.mapper.KlsUserMapper;
import com.example.orderalign.mapper.member.KaiLeShiMemberAlignMapper;
import com.example.orderalign.mapper.member.ThirdPartyMemberDetailMapper;
import com.example.orderalign.mapper.member.YouzanMemberDetailMapper;
import com.example.orderalign.model.KlsUser;
import com.example.orderalign.model.member.KaiLeShiMemberAlign;
import com.example.orderalign.model.member.ThirdPartyMemberDetail;
import com.example.orderalign.model.member.YouzanMemberDetail;
import com.example.orderalign.utils.SignUtil;
import com.youzan.cloud.open.sdk.gen.v1_0_1.model.YouzanScrmCustomerDetailGetResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 异步会员对齐服务，为高并发设计
 */
@Service
@Slf4j
public class KaiLeShiMemberAlignService {

    @Resource
    private KaiLeShiMemberAlignMapper kaiLeShiMemberAlignMapper;
    @Resource
    private ThirdPartyMemberDetailMapper thirdPartyMemberDetailMapper;
    @Resource
    private YouzanMemberDetailMapper youzanMemberDetailMapper;
    @Resource
    private KlsUserMapper klsUserMapper;

    // region 状态常量
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_NOT_FOUND = 4;
    private static final int STATUS_DETAIL_QUERIED = 3;
    private static final int STATUS_PROCESSING_FAILED = 7;
    private static final int STATUS_MAPPING_MISMATCH = 8;
    private static final int DETAIL_STATUS_QUERIED = 1;
    // endregion

    // region API常量
    private static final String API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/getMemberInfo";
    private static final String API_CHANNEL_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/query";
    // endregion

    private OkHttpClient asyncHttpClient;
    private ExecutorService processingExecutor;

    @PostConstruct
    private void init() {
        // 配置OkHttp客户端以支持高并发异步请求
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(200); // 提高整体最大并发请求数
        dispatcher.setMaxRequestsPerHost(200); // 提高针对同一主机的最大并发请求数

        ConnectionPool connectionPool = new ConnectionPool(200, 5, TimeUnit.MINUTES);

        this.asyncHttpClient = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false) // 禁用自动重试，由上层逻辑控制
                .build();

        // 创建一个独立的线程池用于处理CPU密集型任务（如JSON解析、DB操作）
        this.processingExecutor = new ThreadPoolExecutor(
                40, 40,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：使用调用者线程运行，提供反压
        );
    }

    /**
     * 异步处理待对齐的会员信息
     * @param appId 应用ID
     * @param rootKdtId 店铺ID
     */
    public void processPendingMembers(String appId, Long rootKdtId) {
        CompletableFuture.runAsync(() -> {
            log.info("[{}] [ASYNC_TASK_START] 开始异步处理会员详情查询任务", appId);
            // 使用信号量控制并发，防止瞬间请求过多
            final Semaphore semaphore = new Semaphore(100); // 允许100个会员同时处理

            List<KaiLeShiMemberAlign> pendingMembers;
            do {
                pendingMembers = kaiLeShiMemberAlignMapper.selectByStatusWithLimit(STATUS_PENDING, 200);
                if (CollectionUtils.isEmpty(pendingMembers)) {
                    log.info("[{}] [ASYNC_TASK_INFO] 没有需要处理的会员", appId);
                    break;
                }

                log.info("[{}] [ASYNC_TASK_BATCH] 本批次处理会员数量: {}", appId, pendingMembers.size());

                for (KaiLeShiMemberAlign member : pendingMembers) {
                    try {
                        semaphore.acquire();
                        processSingleMemberAsync(member, appId, rootKdtId)
                                .whenComplete((unused, throwable) -> semaphore.release());
                    } catch (InterruptedException e) {
                        log.error("[{}] [ASYNC_TASK_ERROR] 信号量等待被中断", appId, e);
                        Thread.currentThread().interrupt();
                    }
                }
            } while (CollectionUtils.isNotEmpty(pendingMembers));

            log.info("[{}] [ASYNC_TASK_END] 所有批次的会员处理任务已提交", appId);
        }, processingExecutor);
    }

    private CompletableFuture<Void> processSingleMemberAsync(KaiLeShiMemberAlign member, String appId, Long rootKdtId) {
        String mobile = member.getMobile();
        log.info("[{}] [MEMBER_START] 开始处理会员: {}", appId, mobile);

        // 1. 异步查询数云会员详情
        return memberQueryAsync(mobile)
                .thenComposeAsync(kylinDetailStr -> {
                    log.info("[{}] [MEMBER_STEP_1_SUCCESS] 数云详情查询成功: {}", appId, mobile);
                    OutMemberDetail outMemberDetail = parseKylinMemberDetail(kylinDetailStr);
                    if (outMemberDetail == null || StringUtils.isBlank(outMemberDetail.getMemberId())) {
                        log.warn("[{}] [MEMBER_STEP_1_FAIL] 数云会员不存在或数据格式错误: {}", appId, mobile);
                        updateMemberStatus(member, STATUS_NOT_FOUND);
                        return CompletableFuture.<Void>completedFuture(null); // 中断后续流程
                    }

                    // 保存数云详情
                    ThirdPartyMemberDetail thirdPartyMemberDetail = createThirdPartyMemberDetail(outMemberDetail, appId, rootKdtId, mobile);
                    thirdPartyMemberDetailMapper.insert(thirdPartyMemberDetail);
                    log.info("[{}] [MEMBER_DB_SAVE] 数云详情已保存: {}", appId, mobile);

                    // 2. 异步查询有赞会员详情
                    CompletableFuture<YouzanScrmCustomerDetailGetResult> yzFuture = yzMemberQueryAsync(mobile)
                            .thenApply(this::parseYouzanMemberDetail);

                    // 3. 异步查询数云渠道信息
                    CompletableFuture<KLSCustomMemberChannelQueryResponse> channelFuture = memberChannelQueryAsync(outMemberDetail.getMemberId())
                            .thenApply(this::parseKylinChannelInfo);

                    // 4. 组合所有结果进行处理
                    return CompletableFuture.allOf(yzFuture, channelFuture).thenComposeAsync(v -> {
                        YouzanScrmCustomerDetailGetResult yzResult = yzFuture.join();
                        if (yzResult == null || !yzResult.getSuccess() || yzResult.getData() == null) {
                            log.warn("[{}] [MEMBER_STEP_2_FAIL] 有赞会员查询失败: {}", appId, mobile);
                            updateMemberStatus(member, STATUS_NOT_FOUND);
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        log.info("[{}] [MEMBER_STEP_2_SUCCESS] 有赞详情查询成功: {}", appId, mobile);

                        // 保存有赞详情
                        YouzanMemberDetail youzanMemberDetail = createYouzanMemberDetail(yzResult, appId, rootKdtId, mobile);
                        youzanMemberDetailMapper.insertSelective(youzanMemberDetail);
                        log.info("[{}] [MEMBER_DB_SAVE] 有赞详情已保存: {}", appId, mobile);

                        // 验证映射关系
                        KlsUser klsUser = klsUserMapper.selectByMobile(mobile);
                        YouzanScrmCustomerDetailGetResult.YouzanScrmCustomerDetailGetResultData memberData = yzResult.getData();
                        if (!isMappingValid(klsUser, memberData.getYzOpenId(), outMemberDetail.getMemberId())) {
                            log.warn("[{}] [MEMBER_VALIDATION_FAIL] DB预存映射关系与查询结果不符: {}", appId, mobile);
                            updateMemberStatus(member, STATUS_MAPPING_MISMATCH);
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        log.info("[{}] [MEMBER_VALIDATION_SUCCESS] 映射关系验证通过: {}", appId, mobile);

                        KLSCustomMemberChannelQueryResponse channelResponse = channelFuture.join();
                        if (!isChannelInfoValid(channelResponse, memberData.getYzOpenId())) {
                            log.warn("[{}] [MEMBER_STEP_3_FAIL] 数云渠道信息查询失败或验证未通过: {}", appId, mobile);
                            updateMemberStatus(member, STATUS_MAPPING_MISMATCH);
                            return CompletableFuture.<Void>completedFuture(null);
                        }
                        log.info("[{}] [MEMBER_STEP_3_SUCCESS] 数云渠道信息查询并验证通过: {}", appId, mobile);

                        // 所有详情查询和验证完成，更新最终状态
                        member.setStatus(STATUS_DETAIL_QUERIED);
                        member.setYzOpenId(memberData.getYzOpenId());
                        member.setMemberId(outMemberDetail.getMemberId());
                        kaiLeShiMemberAlignMapper.update(member);
                        log.info("[{}] [MEMBER_END_SUCCESS] 会员 {} 全部详情处理完成", appId, mobile);
                        return CompletableFuture.<Void>completedFuture(null);

                    }, processingExecutor);
                }, processingExecutor)
                .exceptionally(ex -> {
                    log.error("[{}] [MEMBER_END_FAIL] 处理会员 {} 过程中发生异常", appId, mobile, ex);
                    updateMemberStatus(member, STATUS_PROCESSING_FAILED);
                    return null;
                });
    }

    // region 异步网络请求封装
    private CompletableFuture<String> memberQueryAsync(String mobile) {
        CompletableFuture<String> future = new CompletableFuture<>();
        String finalMobile = mobile.startsWith("+") ? mobile.replace("+", "%2B") : mobile;
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";
        String url = String.format("%s?memberType=kailas&mobile=%s&pageNo=1&pageSize=50", API_URL, finalMobile);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("X-Caller-Sign", SignUtil.generateSign(callService, contextPath, "v1", timeStamp, serviceSecret, "/youzan/member/getMemberInfo"))
                .addHeader("X-Caller-Timestamp", timeStamp)
                .addHeader("X-Caller-Service", callService)
                .addHeader("Content-Type", "application/json")
                .build();

        asyncHttpClient.newCall(request).enqueue(new OkHttpCallback(future));
        return future;
    }

    private CompletableFuture<String> yzMemberQueryAsync(String mobile) {
        CompletableFuture<String> future = new CompletableFuture<>();
        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, String.format("{\"fields\":\"user_base,level,credit\",\"is_do_ext_point\":false,\"account_info\":{\"account_id\":\"%s\",\"account_type\":2}}", mobile));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.scrm.customer.detail.get/1.0.1?access_token=3d53e273c70c668d7874e48a42eb5d9")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        asyncHttpClient.newCall(request).enqueue(new OkHttpCallback(future));
        return future;
    }

    private CompletableFuture<String> memberChannelQueryAsync(String memberId) {
        CompletableFuture<String> future = new CompletableFuture<>();
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
        asyncHttpClient.newCall(request).enqueue(new OkHttpCallback(future));
        return future;
    }
    // endregion

    // region 数据解析与构建
    private OutMemberDetail parseKylinMemberDetail(String jsonStr) {
        if (StringUtils.isBlank(jsonStr)) return null;
        try {
            JSONArray data = JSON.parseObject(jsonStr).getJSONArray("data");
            if (data != null && !data.isEmpty()) {
                return JSON.parseObject(data.getJSONObject(0).toJSONString(), OutMemberDetail.class);
            }
        } catch (Exception e) {
            log.error("解析数云会员详情失败, JSON: {}", jsonStr, e);
        }
        return null;
    }

    private YouzanScrmCustomerDetailGetResult parseYouzanMemberDetail(String jsonStr) {
        if (StringUtils.isBlank(jsonStr)) return null;
        try {
            return JSON.parseObject(jsonStr, YouzanScrmCustomerDetailGetResult.class);
        } catch (Exception e) {
            log.error("解析有赞会员详情失败, JSON: {}", jsonStr, e);
        }
        return null;
    }

    private KLSCustomMemberChannelQueryResponse parseKylinChannelInfo(String jsonStr) {
        if (StringUtils.isBlank(jsonStr)) return null;
        try {
            JSONObject data = JSON.parseObject(jsonStr).getJSONObject("data");
            if (data != null) {
                return JSON.parseObject(data.toJSONString(), KLSCustomMemberChannelQueryResponse.class);
            }
        } catch (Exception e) {
            log.error("解析数云渠道信息失败, JSON: {}", jsonStr, e);
        }
        return null;
    }

    private ThirdPartyMemberDetail createThirdPartyMemberDetail(OutMemberDetail outMemberDetail, String appId, Long rootKdtId, String mobile) {
        ThirdPartyMemberDetail detail = new ThirdPartyMemberDetail();
        detail.setAppId(appId);
        detail.setKdtId(rootKdtId);
        detail.setMobile(mobile);
        detail.setOutMemberDetail(JSON.toJSONString(outMemberDetail));
        detail.setStatus(DETAIL_STATUS_QUERIED);
        detail.setMemberId(outMemberDetail.getMemberId());
        return detail;
    }

    private YouzanMemberDetail createYouzanMemberDetail(YouzanScrmCustomerDetailGetResult result, String appId, Long rootKdtId, String mobile) {
        YouzanMemberDetail detail = new YouzanMemberDetail();
        detail.setAppId(appId);
        detail.setKdtId(rootKdtId);
        detail.setMobile(mobile);
        detail.setYouzanMemberDetail(JSON.toJSONString(result));
        detail.setStatus(DETAIL_STATUS_QUERIED);
        if (result.getData() != null) {
            detail.setYzOpenId(result.getData().getYzOpenId());
        }
        return detail;
    }
    // endregion

    // region 验证与状态更新
    private boolean isMappingValid(KlsUser klsUser, String yzOpenId, String outOpenId) {
        if (klsUser == null) return false;
        return Objects.equals(klsUser.getYzOpenId(), yzOpenId) && Objects.equals(klsUser.getOutOpenId(), outOpenId);
    }

    private boolean isChannelInfoValid(KLSCustomMemberChannelQueryResponse channelResponse, String yzOpenId) {
        if (channelResponse == null || CollectionUtils.isEmpty(channelResponse.getChannelInfoList())) {
            return false;
        }
        return channelResponse.getChannelInfoList().stream()
                .anyMatch(m -> "YOUZAN".equals(m.getChannelType())
                        && yzOpenId.equals(m.getCustomerNo()));
    }

    private void updateMemberStatus(KaiLeShiMemberAlign member, int status) {
        member.setStatus(status);
        kaiLeShiMemberAlignMapper.update(member);
    }
    // endregion

    /**
     * 内部类，用于处理OkHttp异步回调
     */
    private static class OkHttpCallback implements Callback {
        private final CompletableFuture<String> future;

        public OkHttpCallback(CompletableFuture<String> future) {
            this.future = future;
        }

        @Override
        public void onFailure(Call call, IOException e) {
            future.completeExceptionally(e);
        }

        @Override
        public void onResponse(Call call, Response response) {
            try (ResponseBody body = response.body()) {
                if (response.isSuccessful() && body != null) {
                    future.complete(body.string());
                } else {
                    String errorBody = body != null ? body.string() : "null";
                    future.completeExceptionally(new IOException("Unexpected code " + response + ", body: " + errorBody));
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        }
    }
}