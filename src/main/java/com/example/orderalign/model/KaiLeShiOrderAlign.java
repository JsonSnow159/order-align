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
     * 推送状态，0-待查询，1-已对齐，2-对齐失败，3-已查询详情，4-详情查询失败
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;
}
