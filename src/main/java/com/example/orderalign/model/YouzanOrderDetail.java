package com.example.orderalign.model;

import lombok.Data;

import java.util.Date;

@Data
public class YouzanOrderDetail {
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
     * 有赞订单详情
     */
    private String tidDetail;

    /**
     * 查询状态，0-待查询，1-已查询，2-已对账，3-查询有赞详情失败，4-查询数云详情失败
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
