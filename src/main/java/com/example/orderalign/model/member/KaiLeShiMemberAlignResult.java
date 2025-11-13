package com.example.orderalign.model.member;

import lombok.Data;

import java.util.Date;

@Data
public class KaiLeShiMemberAlignResult {
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
    /*** yzOpenId */
    private String yzOpenId;
    /*** memberId */
    private String memberId;

    //有赞姓名
    private String yzName;
    //三方姓名
    private String outName;
    //姓名对齐结果
    private String nameResult;

    //有赞性别
    private Integer yzGender;
    //三方性别
    private String outGender;
    //性别对齐结果
    private String genderResult;

    //有赞生日
    private String yzBirthday;
    //三方生日
    private String outBirthday;
    //生日对齐结果
    private String birthdayResult;


    //成为客户渠道
    private Integer yzCustomerChannel;
    //成为会员渠道
    private Integer yzChannel;
    //三方成为会员渠道
    private String outChannel;
    //渠道对齐结果
    private String channelResult;


    //有赞成为会员门店
    private String yzShopId;
    private String yzShopNo;
    //三方成为会员店铺
    private String outShop;
    //店铺对齐结果
    private String shopResult;

    //有赞省市区
    private String yzAddress;
    //数云省市区
    private String outAddress;
    //省市区对齐结果
    private String addressResult;


    //有赞会员创建时间
    private String yzCreateTime;
    //三方会员创建时间
    private String outCreateTime;
    //成为会员时间对齐结果
    private String createTimeResult;

    //有赞等级
    private String yzLevel;
    //三方等级
    private String outLevel;
    //等级对齐结果
    private String levelResult;

    //有赞积分
    private Integer yzPoint;
    //三方积分
    private Integer outPoint;
    //积分对齐结果
    private String pointResult;

    /**
     * 创建时间
     */
    private Date createdAt;
    /**
     * 更新时间
     */
    private Date updatedAt;
}
