package com.example.orderalign.model.member;

import lombok.Data;

import java.util.Date;

/**
 * @author jincai.wu
 * @date 2025/11/13
 */
@Data
public class YouzanMemberDetail {
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
     * 手机号
     */
    private String mobile;

    /**
     * 有赞open id
     */
    private String yzOpenId;

    /**
     * 有赞会员详情
     */
    private String youzanMemberDetail;

    /**
     * 查询状态，0-待查询，1-已查询
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
