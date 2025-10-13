package com.example.orderalign.model;

import lombok.Data;

import java.util.Date;

@Data
public class KaiLeShiNoSourceRefundOrderAlignResult {
    /**
     * 主键
     */
    private Long id;

    /**
     * 店铺id
     */
    private Long kdtId;

    /**
     * app id
     */
    private String appId;

    /**
     * 有赞订单id
     */
    private String refundId;

    /**
     * 外部订单id
     */
    private String outRefundId;

    //下单店铺id
    private Long nodeKdtId;
    //有赞映射的店铺编码
    private String yzShopNo;
    //数云店铺编码
    private String outShopNo;
    //店铺对齐结果
    private String shopResult;

    //有赞退款金额
    private Long yzRefundFee;
    //数云实退金额
    private Long outRefundFee;
    //实退金额对齐结果
    private String refundFeeResult;


    private String userId;
    //有赞映射的会员id
    private String yzMemberId;
    //映射的会员id
    private String outOpenId;
    //数云渠道用户id
    private String customerNo;
    //数云会员id
    private String outMemberId;
    //会员对齐结果
    private String memberIdResult;


    //是否含虚拟商品
    private String isMockItemId;

    //商品对齐结果
    private String subOrderResult;
    //商品未对齐的明细
    private String subOrderFailReason;

    /**
     * 创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     */
    private Date updatedAt;
}
