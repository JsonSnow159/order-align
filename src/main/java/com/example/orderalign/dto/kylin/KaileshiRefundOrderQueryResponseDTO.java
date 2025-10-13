package com.example.orderalign.dto.kylin;

import lombok.Data;

import java.util.List;

/**
 * 凯乐石拉取线下退单响应对象DTO
 */
@Data
public class KaileshiRefundOrderQueryResponseDTO extends KaileshiBaseResponseDTO {
    //{
    //            "refundFee": 100.00,
    //            "orderId": "refund_test20230424-5",
    //            "freight": 10.000,
    //            "refundOrderItems": [],
    //            "shopName": "SHOP001",
    //            "orderStatus": "REFUND_FINISHED",
    //            "description": "无",
    //            "channelType": "TAOBAO",
    //            "receiverProvince": "上海",
    //            "receiverCity": "上海",
    //            "lastSync": "2023-04-24T05:36:44.316Z",
    //            "totalQuantity": 1,
    //            "receiverDistrict": "浦东新区",
    //            "id": "refund_test20230424-5",
    //            "shopTypeCode": "TAOBAO",
    //            "memberId": "K202304240000002",
    //            "shopCode": "SHOP001",
    //            "finishTime": "2023-04-24T03:11:55.005Z",
    //            "receiverName": "abc",
    //            "receiverMobile": "18900000000",
    //            "updateTime": "2023-04-24T05:36:44.316Z",
    //            "receiverAddress": "南京东路100号",
    //            "isInternal": "N",
    //            "memberType": "kailas",
    //            "customerNo": "T000001"
    //        }

    private Double refundFee;

    private String refundTime;

    private String orderId;

    private String freight;

    private String shopName;

    // 仅同步退款完成的， REFUND_FINISHED
    private String orderStatus;

    private String description;

    private String channelType;

    private String receiverProvince;

    private String receiverCity;

    private String receiverDistrict;

    private String lastSync;

    private Integer totalQuantity;

    private String id;

    private String shopTypeCode;

    private String memberId;

    private String shopCode;

    private String finishTime;

    private String receiverName;

    private String receiverMobile;

    private String updateTime;

    private String receiverAddress;

    private String isInternal;

    private String memberType;

    private String customerNo;

    private List<KaileshiOrderRefundQuerySubItemResponseDTO> refundOrderItems;
}
