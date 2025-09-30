package com.example.orderalign.dto.kylin;

import lombok.Data;

import java.io.Serializable;

/**
 * 凯乐石订单查询返回信息，商品对象DTO
 */
@Data
public class KaileshiOrderQuerySubItemResponseDTO implements Serializable {

    //    "shopCode":"SHOP001",
    //    "discountRate":1,
    //    "orderType":"NORMAL",
    //    "discountFee":0,
    //    "quantity":1,
    //    "orderId":"1001",
    //    "orderItemId":"100101",
    //    "shopName":"SHOP001",
    //    "channelType":"WECHAT_MALL",
    //    "updateTime":"2023-07-30T06:48:42.123Z",
    //    "productName":"ceshishang",
    //    "picture":[],
    //    "productCode":"101",
    //    "orderTime":"2023-07-28T05:24:55.000Z",
    //    "lastSync":"2023-07-30T06:48:42.194Z",
    //    "totalFee":0,
    //    "payment":0,
    //    "id":"100101",
    //    "memberType":"kailas",
    //    "status":"CREATED",
    //    "memberId":"K202307300000002"

    private String shopCode;

    private Integer discountRate;

    private String orderType;

    private Double discountFee;

    private Integer quantity;

    private String orderId;

    private String orderItemId;

    private String shopName;

    private String channelType;

    private String updateTime;

    private String productName;

    private String picture;

    private String productCode;

    private String orderTime;

    private String lastSync;

    private Double totalFee;

    private Double payment;

    private String id;

    private String memberType;

    private String status;

    private String memberId;

    private String skuId;
    //退款原 子订单id
    private String originOrderItemId;



}
