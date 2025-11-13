package com.example.orderalign.dto.member;

import com.youzan.cloud.connector.sdk.client.BaseExtResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @Author:吴金才
 * @Date:2025/4/27 09:46
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KLSCustomMemberChannelQueryResponse extends BaseExtResponse {
    /*** 会员ID 全网唯一标识符, 最大长度为32 */
    private String memberId;
    /*** 会员类型 固定值 kailas */
    private String memberType;
    /*** 手机号 */
    private String mobile;
    /*** 渠道信息 */
    private List<ChannelInfo> channelInfoList;

    @Data
    @NoArgsConstructor
    public static class ChannelInfo {
        /*** 渠道编码 TAOBAO(淘宝) JD(京东) YOUZAN(有赞) POS(POS) WECHAT(会员中心小程序) DOUYIN(抖音)*/
        private String channelType;
        /*** 渠道ID */
        private String customerNo;
    }
}
