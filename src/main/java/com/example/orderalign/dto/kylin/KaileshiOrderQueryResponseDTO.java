package com.example.orderalign.dto.kylin;

import lombok.Data;

import java.util.List;

/**
 * 凯乐石正向订单查询返回对象DTO
 *
 */
@Data
public class KaileshiOrderQueryResponseDTO extends KaileshiBaseResponseDTO {
    //"shopCode":"SHOP001",
    //"orderType":"NORMAL",
    //"orderId":"1001",
    //"payTime":"2023-07-28T05:24:55.000Z",
    //"pointFlag":1,
    //"shopName":"SHOP001",
    //"orderStatus":"CREATED",
    //"channelType":"WECHAT_MALL",
    //"updateTime":"2023-07-30T06:48:42.123Z",
    //"orderItems":[]
    //"isInternal":"N",
    //"totalQuantity":1,
    //"orderTime":"2023-07-28T05:24:55.000Z",
    //"lastSync":"2023-07-30T06:48:42.194Z",
    //"totalFee":0,
    //"payment":0,
    //"isSend":"N",
    //"memberType":"kailas",
    //"id":"1001",
    //"customerNo":"wx25b3904844561169_oytsN5HIjsK5ZYX7FEh4PToExW7I",
    //"memberId":"K202307300000002"

    private String shopCode;

    private String orderType;

    private String orderId;

    private String payTime;

    private Integer pointFlag;

    private String shopName;

    private String orderStatus;

    private String channelType;

    private String updateTime;

    private String isInernal;

    private Integer totalQuantity;

    private String orderTime;

    private String lastSync;

    private Double totalFee;

    private Double payment;

    private Double discountFee;

    private String isSend;

    private String memberType;

    private String id;

    private String customerNo;

    private String memberId;
    //导购编码
    private String guideCode;
    //退款原 订单id
    private String originOrderId;
    //渠道Id
    private String channelId;
    //是否会员
    private Boolean isMember;

    private List<KaileshiOrderQuerySubItemResponseDTO> orderItems;

}
