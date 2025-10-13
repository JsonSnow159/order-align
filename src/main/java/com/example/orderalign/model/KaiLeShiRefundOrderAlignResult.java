package com.example.orderalign.model;

import lombok.Data;

import java.util.Date;

@Data
public class KaiLeShiRefundOrderAlignResult {
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

    //有赞创建时间
    private String yzCreateTime;
    //三方创建时间
    private String outCreateTime;
    //创建时间对齐结果
    private String createTimeResult;

    //下单店铺id
    private Long nodeKdtId;
    //有赞映射的店铺编码
    private String yzShopNo;
    //数云店铺编码
    private String outShopNo;
    //店铺对齐结果
    private String shopResult;

    //有赞实付金额
    private Long yzPayment;
    //数云实付金额
    private Long outPayment;
    //实付金额对齐结果
    private String paymentResult;

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
