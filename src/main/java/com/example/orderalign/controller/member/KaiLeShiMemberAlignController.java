package com.example.orderalign.controller.member;

import com.alibaba.fastjson.JSON;
import com.example.orderalign.dto.member.KLSCustomMemberChannelQueryResponse;
import com.example.orderalign.dto.member.MemberAlignDTO;
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
import com.youzan.cloud.connector.sdk.client.YzCloudResponse;
import com.youzan.cloud.connector.sdk.common.exception.RecoverableException;
import com.youzan.cloud.connector.sdk.infra.dal.mapper.InfraUserRelationMapper;
import com.youzan.cloud.open.sdk.gen.v1_0_1.model.YouzanScrmCustomerDetailGetResult;
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
@RequestMapping("/kaileshi/datafix")
public class KaiLeShiMemberAlignController {
    @Resource
    private KaiLeShiMemberAlignMapper kaiLeShiMemberAlignMapper;
    @Resource
    private ThirdPartyMemberDetailMapper thirdPartyMemberDetailMapper;
    @Resource
    private YouzanMemberDetailMapper youzanMemberDetailMapper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private KlsUserMapper klsUserMapper;
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
    private static final String API_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/getMemberInfo";
    private static final String API_CHANNEL_URL = "https://api-ekailas.kylin.shuyun.com/omni-api/v1/youzan/member/query";
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

    /**
     * 执行顺序
     * 2张表，会员映射、退单映射
     * 1、先上传数云会员号
     * 2、查询数云的会员详情，存起来；
     * 3、判断具体会员类型，查询映射表，获取会员映射，会员映射区分一下会员映射
     * 4、
     *
     * @param param
     * @return
     */
    @PostMapping("/uploadMember")
    public YzCloudResponse<Object> uploadOrder(@RequestBody MemberAlignDTO param) {
        log.info("凯乐石会员对齐param:{}", param);
        try {
            String appId = param.getAppId();
            if (StringUtils.isBlank(appId)) {
                return YzCloudResponse.error(400, "appId is required");
            }

            List<String> mobileList = new ArrayList<>();
            if (StringUtils.isNotBlank(param.getMobile())) {
                mobileList.add(param.getMobile());
            }

            if (CollectionUtils.isNotEmpty(param.getMobileList())) {
                mobileList.addAll(param.getMobileList());
            }

            if (CollectionUtils.isEmpty(mobileList)) {
                return YzCloudResponse.success("mobileList is empty");
            }

            String[] appIdArr = appId.split("_");
            long rootKdtId = Long.parseLong(appIdArr[0]);

            for (String mobile : mobileList) {
                KaiLeShiMemberAlign existRecord = kaiLeShiMemberAlignMapper.selectByAppIdAndMobile(appId, mobile);
                if (Objects.nonNull(existRecord)) {
                    log.warn("mobile: {} already exists, skipping.", mobile);
                    continue;
                }

                KaiLeShiMemberAlign kaiLeShiMemberAlign = new KaiLeShiMemberAlign();
                kaiLeShiMemberAlign.setAppId(appId);
                kaiLeShiMemberAlign.setKdtId(rootKdtId);
                kaiLeShiMemberAlign.setMobile(mobile);
                kaiLeShiMemberAlign.setStatus(STATUS_PENDING);
                kaiLeShiMemberAlignMapper.insert(kaiLeShiMemberAlign);
                log.info("mobile: {} inserted for processing.", mobile);
            }
        } catch (Exception e) {
            log.error("处理失败", e);
            return YzCloudResponse.error(500, "处理失败:" + e.getMessage());
        }
        return YzCloudResponse.success();
    }

    @SneakyThrows
    @PostMapping("/queryOutMember")
    public YzCloudResponse<Object> queryOutDetail(@RequestBody MemberAlignDTO param) {
        log.info("开始查询会员详情");
        String appId = param.getAppId();
        String[] appIdArr = appId.split("_");
        String tripartite = appIdArr[1];
        Long rootKdtId = param.getRootKdtId();
//        Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);

        String lockKey = String.format("queryDetail_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},会员详情查询正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }

        try {
            List<KaiLeShiMemberAlign> pendingOrders = kaiLeShiMemberAlignMapper.selectByStatus(STATUS_PENDING);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的会员");
                return YzCloudResponse.success();
            }

            log.info("本批次处理会员数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(memberAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String mobile = memberAlign.getMobile();

                            ThirdPartyMemberDetail thirdPartyMemberDetail = new ThirdPartyMemberDetail();
                            thirdPartyMemberDetail.setAppId(appId);
                            thirdPartyMemberDetail.setKdtId(rootKdtId);
                            thirdPartyMemberDetail.setMobile(mobile);

                            OutMemberDetail outMemberDetail = new OutMemberDetail();
                            String kylinMemberDetailStr = memberQuery(mobile);
                            if (StringUtils.isNotBlank(kylinMemberDetailStr)) {
                                outMemberDetail = JSON.parseObject(JSON.toJSONString(JSON.parseObject(kylinMemberDetailStr).getJSONArray("data").getJSONObject(0)), OutMemberDetail.class);
                            }
                            if (Objects.isNull(outMemberDetail)) {
                                memberAlign.setStatus(STATUS_NOT_FOUND);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                log.warn("查询数云会员详情失败, mobile: {}", mobile);
                                return;
                            }
                            thirdPartyMemberDetail.setOutMemberDetail(JSON.toJSONString(outMemberDetail));
                            thirdPartyMemberDetail.setStatus(DETAIL_STATUS_QUERIED);
                            thirdPartyMemberDetail.setMemberId(outMemberDetail.getMemberId());
                            thirdPartyMemberDetailMapper.insert(thirdPartyMemberDetail);
                            //三方详情已查询
                            memberAlign.setStatus(STATUS_OUT_DETAIL_QUERIED);
                            kaiLeShiMemberAlignMapper.update(memberAlign);
                        } catch (Exception e) {
                            log.error("处理单个用户失败 mobile: {}", memberAlign.getMobile(), e);
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
        log.info("查询会员Detail任务结束");
        return YzCloudResponse.success();
    }

    @SneakyThrows
    @PostMapping("/queryYzDetail")
    public YzCloudResponse<Object> queryYzDetail(@RequestBody MemberAlignDTO param) {
        log.info("开始查询会员详情");
        String appId = param.getAppId();
        String[] appIdArr = appId.split("_");
        String tripartite = appIdArr[1];
        Long rootKdtId = param.getRootKdtId();
//        Map<String, Object> props = globalRoutePropsFetcher.fetchAllProps(rootKdtId, tripartite);

        String lockKey = String.format("queryYzMemberDetail_%s", param.getAppId());
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock(1, 5, TimeUnit.MINUTES);
        if (!isLock) {
            log.warn("获取锁失败,appId:{},会员详情查询正在处理中,lockKey: {}", param.getAppId(), lockKey);
            throw new RecoverableException("获取锁失败");
        }

        try {
            List<KaiLeShiMemberAlign> pendingOrders = kaiLeShiMemberAlignMapper.selectByStatus(STATUS_FOUND);
            if (CollectionUtils.isEmpty(pendingOrders)) {
                log.info("没有需要处理的会员");
                return YzCloudResponse.success();
            }

            log.info("本批次处理会员数量: {}", pendingOrders.size());

            List<CompletableFuture<Void>> futures = pendingOrders.stream()
                    .map(memberAlign -> CompletableFuture.runAsync(() -> {
                        try {
                            String mobile = memberAlign.getMobile();
                            String yzMemberDetail = yzMemberQuery(mobile);
                            YouzanScrmCustomerDetailGetResult youzanMemberDetailResult = JSON.parseObject(yzMemberDetail, YouzanScrmCustomerDetailGetResult.class);
                            if (Objects.isNull(youzanMemberDetailResult) || !youzanMemberDetailResult.getSuccess()) {
                                memberAlign.setStatus(STATUS_NOT_FOUND);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            YouzanMemberDetail youzanMemberDetail = new YouzanMemberDetail();
                            youzanMemberDetail.setAppId(appId);
                            youzanMemberDetail.setKdtId(rootKdtId);
                            youzanMemberDetail.setMobile(mobile);
                            YouzanScrmCustomerDetailGetResult.YouzanScrmCustomerDetailGetResultData memberData = youzanMemberDetailResult.getData();
                            youzanMemberDetail.setYzOpenId(memberData.getYzOpenId());
                            youzanMemberDetail.setYouzanMemberDetail(JSON.toJSONString(youzanMemberDetailResult));
                            youzanMemberDetail.setStatus(DETAIL_STATUS_QUERIED);
                            youzanMemberDetailMapper.insertSelective(youzanMemberDetail);

                            //对齐映射
                            KlsUser klsUser = klsUserMapper.selectByMobile(mobile);
                            if (Objects.isNull(klsUser)) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            String yzOpenId = klsUser.getYzOpenId();
                            String outOpenId = klsUser.getOutOpenId();
                            if (Objects.equals(yzOpenId, memberData.getYzOpenId())) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            } else if (Objects.equals(memberAlign.getMemberId(), outOpenId)) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            String channelQueryResult = memberChannelQuery(klsUser.getOutOpenId());
                            if (StringUtils.isBlank(channelQueryResult)) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            KLSCustomMemberChannelQueryResponse klsCustomMemberChannelQueryResponses = JSON.parseObject(channelQueryResult, KLSCustomMemberChannelQueryResponse.class);
                            if (CollectionUtils.isEmpty(klsCustomMemberChannelQueryResponses.getChannelInfoList())) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            List<KLSCustomMemberChannelQueryResponse.ChannelInfo> youzanChannels = klsCustomMemberChannelQueryResponses.getChannelInfoList().stream()
                                    .filter(m -> Objects.equals(m.getChannelType(), "YOUZAN")
                                            && StringUtils.isNotBlank(m.getCustomerNo())
                                            && m.getCustomerNo().equals(outOpenId))
                                    .collect(Collectors.toList());
                            if (CollectionUtils.isEmpty(youzanChannels)) {
                                memberAlign.setStatus(8);
                                kaiLeShiMemberAlignMapper.update(memberAlign);
                                return;
                            }
                            memberAlign.setStatus(STATUS_DETAIL_QUERIED);
                            kaiLeShiMemberAlignMapper.update(memberAlign);
                        } catch (Exception e) {
                            memberAlign.setStatus(7);
                            kaiLeShiMemberAlignMapper.update(memberAlign);
                            log.error("处理单个会员失败 mobile: {}", memberAlign.getMobile(), e);
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
        log.info("查询会员Detail任务结束");
        return YzCloudResponse.success();
    }

    public static String memberQuery(String mobile) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";

        String url = String.format("%s?memberType=kailas&mobile=%s&pageNo=1&pageSize=50",
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

    public static String yzMemberQuery(String mobile) throws IOException {
        MediaType mediaType = MediaType.parse("application/json");
        okhttp3.RequestBody body = okhttp3.RequestBody.create(mediaType, String.format("{\"fields\":\"user_base,level,credit\",\"is_do_ext_point\":false,\"account_info\":{\"account_id\":\"%s\",\"account_type\":2}}", mobile));
        Request request = new Request.Builder()
                .url("https://open.youzanyun.com/api/youzan.scrm.customer.detail.get/1.0.1?access_token=c7330263b90c63ec08211c84a2a3648")
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Cookie", "acw_tc=7b678af2367d8aad51e3ea914ac679a83be97eaf38aaac7c82ddc27bb77baf36")
                .build();
        Response response = client.newCall(request).execute();
        String responseStr = response.body().string();
        return responseStr;
    }

    public static String memberChannelQuery(String memberId) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String callService = "omni-api";
        String contextPath = "omni-api";
        String serviceSecret = "gdis22kslllk2";

        String url = String.format("%s?memberType=kailas&memberId=%s",
                API_CHANNEL_URL, memberId);

        Request request = new Request.Builder()
                .url(url)
                .method("GET", null)
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
