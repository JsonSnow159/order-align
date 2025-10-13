package com.example.orderalign.dto.kylin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 凯乐石退单请求参数DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaileshiRefundOrderQueryRequestDTO implements Serializable {
    //memberType	Y	string	会员类型	kailas/vaude
    //channelType	N	string	渠道	TAOBAO/DOUYIN/POS/O2O
    //memberId	N	string	会员ID 会员ID, 与 mobile 二选一, 两个都有以 memberId 为主	K202304250000014
    //lastSyncBeginTime	Y	string	以lastSync作为时间起搜索，格式为yyyy-MM-dd HH:mm:ss
    //lastSyncEndTime	Y	string	以lastSync作为时间止搜索，格式为yyyy-MM-dd HH:mm:ss	2023-04-25 13:55:06
    //pageNo	Y	Integer	分页页数
    //pageSize	Y	Integer	每页记录数（不大于50）

    /**
     * 会员类型	kailas,必填
     */
    private String memberType;

    /**
     * 数云会员id
     */
    private String memberId;

    /**
     * 渠道	TAOBAO/DOUYIN/POS/O2O
     */
    private String channelType;

    /**
     * 以lastSync作为时间起搜索，格式为[yyyy-MM-dd HH:mm:ss]， 必填
     */
    private String lastSyncBeginTime;

    /**
     * 以lastSync作为时间止搜索，格式为[yyyy-MM-dd HH:mm:ss]， 必填
     */
    private String lastSyncEndTime;

    /**
     * 分页页数, 默认1, 必填
     */
    private Integer pageNo;

    /**
     * 每页记录数（不大于50）, 默认50， 必填
     */
    private Integer pageSize;

    /*** 订单id */
    private String orderId;
    /*** 店铺编码 */
    private String shopCode;
    /**
     * 订单状态
     * REFUND_CANCEL 取消
     * REFUND_FINISHED 完成 REFUNDING 退款中 CREATED 创建 REFUND_START 退款开始
     * SELLING_REFUND_FINISHED 售中退款完成
     */
    private String status;

}
