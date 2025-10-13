package com.example.orderalign.dto.kylin;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.io.Serializable;

/**
 * 凯乐石订单查询返回信息，商品对象DTO
 */
@Data
public class KaileshiOrderRefundQuerySubItemResponseDTO implements Serializable {

    //                    "refundFee": 470.000,
    //                    "finishTime": "2023-04-25T05:45:53.000Z",
    //                    "quantity": 1,
    //                    "orderItemId": "TD202304251345-01",
    //                    "channelType": "TAOBAO",
    //                    "updateTime": "2023-04-25T05:45:52.375Z",
    //                    "productName": "测试商品2",
    //                    "picture": [],
    //                    "lastSync": "2023-04-25T05:45:52.375Z",
    //                    "productCode": "SP02",
    //                    "originOrderItemId": "ZD202304251343-02",
    //                    "id": "TD202304251345-01",
    //                    "originOrderId": "ZD202304251343",
    //                    "status": "REFUND_FINISHED"

    private String originOrderId;

    private Double refundFee;

    private String finishTime;

    private Integer quantity;

    private String orderItemId;

    private String channelType;

    private String updateTime;

    private String productName;

//    private String picture;

    private String lastSync;

    private String productCode;

    private String originOrderItemId;
    //原子单id
    @JSONField(name = "originOrderItemId_id")
    private String originOrderItemIdId;

    private String id;

    private String status;

    private String skuId;
}
