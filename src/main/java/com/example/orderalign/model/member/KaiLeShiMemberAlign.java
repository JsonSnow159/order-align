package com.example.orderalign.model.member;

import lombok.Data;

import java.util.Date;

/**
 * @author jincai.wu
 * @date 2025/9/18
 */
@Data
public class KaiLeShiMemberAlign {
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
     * mobile
     */
    private String mobile;

    /**
     * yzOpenId
     */
    private String yzOpenId;

    /**
     * 数云memberId
     */
    private String memberId;

    /**
     * 推送状态，0-待查询三方详情，
     * 1-已对齐有赞映射，
     * 2-对齐失败，
     * 3-已查询全部详情，
     * 4-数云详情查询失败，
     * 5-有赞详情查询失败,
     * 7-插入DB失败
     * 8-映射异常
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
