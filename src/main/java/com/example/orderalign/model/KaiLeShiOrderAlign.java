package com.example.orderalign.model;

import lombok.Data;

import java.util.Date;

/**
 * @author jincai.wu
 * @date 2025/9/18
 */
@Data
public class KaiLeShiOrderAlign {
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

    /**
     * 推送状态，0-待查询三方详情，1-已对齐有赞映射，2-对齐失败，3-已查询全部详情，4-详情查询失败，5-已查询三方详情，待查询有赞详情
     */
    private Integer status;

    /**
     * 正常订单
     * 正常退单
     * 正向退单：正向单里面产生的退款单，退款数量、金额为负数，退单中需要取绝对值
     * 逆向订单：逆向单里面产生的正向单，退款数量、金额为负数，订单中需要取绝对值
     */
    private String type;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
