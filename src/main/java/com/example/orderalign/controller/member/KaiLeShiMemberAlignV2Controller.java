package com.example.orderalign.controller.member;

import com.example.orderalign.dto.member.MemberAlignDTO;
import com.example.orderalign.service.KaiLeShiMemberAlignService;
import com.youzan.cloud.connector.sdk.client.YzCloudResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/kaileshi/member/v2")
public class KaiLeShiMemberAlignV2Controller {

    @Resource
    private KaiLeShiMemberAlignService kaiLeShiMemberAlignService;

    /**
     * 异步触发会员详情查询与对齐任务
     * <p>
     * 该接口会立即返回，并在后台启动一个异步任务来处理所有待处理的会员。
     * 任务状态和结果需要通过查询数据库或日志来跟踪。
     *
     * @param param 包含appId和rootKdtId的请求体
     * @return 接受请求，任务已在后台开始
     */
    @PostMapping("/queryOutMember")
    public ResponseEntity<YzCloudResponse<Object>> queryOutDetailAsync(@RequestBody MemberAlignDTO param) {
        log.info("V2 - 接收到会员详情异步处理请求, param: {}", param);

        String appId = param.getAppId();
        Long rootKdtId = param.getRootKdtId();

        if (appId == null || rootKdtId == null) {
            return ResponseEntity.badRequest().body(YzCloudResponse.error(400, "appId and rootKdtId are required"));
        }

        // 调用异步服务，立即返回
        kaiLeShiMemberAlignService.processPendingMembers(appId, rootKdtId);

        String message = "任务已接受并在后台处理 (Task accepted and is processing in the background)";
        // 返回202 Accepted状态，表示请求已被接受，但处理尚未完成
        return ResponseEntity.accepted().body(YzCloudResponse.success(message));
    }
}
