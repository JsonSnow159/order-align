package com.example.orderalign.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class OutOrderDetail {
    //外部单号
    private String outTid;
    //下单时间
    private String createTime;
    //支付时间
    private String payTime;
    //应付总金额
    private Long totalAmount;
    //实付总金额
    private Long totalPayAmount;
    //优惠总额
    private Long totalDiscountAmount;
    //下单店铺
    private String shopCode;
    //渠道用户id
    private String customerNo;
    //会员id
    private String memberId;
    //下单渠道
    private String channel;
    //导购编码
    private String guideCode;

    private List<SubOrder> oidList;

    @Data
    @NoArgsConstructor
    public static class SubOrder {
        //商品编码
        private String itemNo;
        //规格编码
        private String skuNo;
        //商品数量
        private Integer num;
        //应付总额
        private Long totalFee;
        //实付总额
        private Long payment;
        //商品名称
        private String title;
        //三方子订单id
        private String outOid;
    }
}
