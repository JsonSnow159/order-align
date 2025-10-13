package com.example.orderalign.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class YzOrderDetail {
    private String tid;
    private Long kdtId;
    //有赞会员ID
    private String yzOpenId;
    //无原单的userId
    private String userId;
    //手机号
    private String mobile;
    //渠道
    private String channel;
    //创建时间
    private String createTime;
    //支付时间
    private String payTime;
    //订单应付总金额
    private Long totalAmount;
    //订单实付总金额
    private Long totalPayAmount;
    //订单优惠总金额
    private Long totalDiscountAmount;
    //子订单
    private List<SubOrder> oidList;

    @Data
    @NoArgsConstructor
    public static class SubOrder {
        //有赞商品id
        private Long itemId;
        //有赞规格id
        private Long skuId;
        //商品编码
        private String itemNo;
        //规格编码
        private String skuNo;
        //商品数量
        private Integer num;
        //商品单价
        private Long price;
        //商品现价
        private Long discountPrice;
        //优惠金额
        private Long discountAmount;
        //子订单应付总额
        private Long totalAmount;
        //子订单实付金额
        private Long payment;
        //商品名称
        private String title;
        //导购编码列表
        private List<String> daogous;
        //外部子订单id
        private String outOid;
        //有赞子订单id
        private String oid;
    }
}
