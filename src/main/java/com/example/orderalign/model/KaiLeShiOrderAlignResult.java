package com.example.orderalign.model;

import lombok.Data;

import java.util.Date;

@Data
public class KaiLeShiOrderAlignResult {
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
    private String tid;

    /**
     * 外部订单id
     */
    private String outTid;

    //有赞创建时间
    private String yzCreateTime;
    //三方创建时间
    private String outCreateTime;
    //创建时间对齐结果
    private String createTimeResult;

    //有赞支付时间
    private String yzPayTime;
    //三方支付时间
    private String outPayTime;
    //支付时间对齐结果
    private String payTimeResult;



    //下单店铺id
    private Long nodeKdtId;
    //有赞映射的店铺编码
    private String yzShopNo;
    //数云店铺编码
    private String outShopNo;
    //店铺对齐结果
    private String shopResult;


    //有赞应付金额
    private Long yzTotalAmount;
    //数云应付金额
    private Long outTotalAmount;
    //应付金额对齐结果
    private String totalAmountResult;

    //有赞优惠金额
    private Long yzDiscountAmount;
    //数云优惠金额
    private Long outDiscountAmount;
    //优惠金额对齐结果
    private String discountAmountResult;

    //有赞实付金额
    private Long yzPayment;
    //数云实付金额
    private Long outPayment;
    //实付金额对齐结果
    private String paymentResult;

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

    //有赞映射的channel
    private String yzChannel;
    //数云channel
    private String outChannel;
    //channel对齐结果
    private String channelResult;


    //是否含虚拟商品
    private String isMockItemId;

    //商品对齐结果
    private String subOrderResult;
    //商品未对齐的明细
    private String subOrderFailReason;

    //有赞映射导购编码
    private String yzGuideNoList;
    //数云导购编码列表
    private String outGuideNoList;
    //导购对齐结果
    private String itemGuideResult;
    /**
     * 创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     */
    private Date updatedAt;
}
