package com.example.orderalign.dto.member;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class OutMemberDetail {
    /**
     * 数云店铺编号
     */
    private String shopCode;

    /**
     * 性别，M 男；  女
     */
    private String gender;

    /**
     * 注册时间,   2023-04-24T03:31:23.617Z 0时区的
     */
    private String registerTime;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 会员名称
     */
    private String memberName;

    private String shopName;

    /**
     * 生日， yyyy-MM-dd
     */
    private String dateOfBirth;

    private String identityCard;

    /**
     * 数云卡号，用不上
     */
    private String cardNo;

    /**
     * 会员类型
     */
    private String memberType;

    /**
     * 数云会员卡号
     */
    private String memberId;

    /**
     * 积分
     */
    private Integer point;
    /**
     * 等级名称
     */
    private String memberGrade;
    /**
     * 首次注册渠道
     */
    private String firstRegisterChannelType;
    /*** 省 */
    private String provinceName;
    /*** 市 */
    private String cityName;
    /*** 区 */
    private String districtName;
}
