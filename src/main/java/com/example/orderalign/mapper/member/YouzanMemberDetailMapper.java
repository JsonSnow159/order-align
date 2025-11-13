package com.example.orderalign.mapper.member;

import com.example.orderalign.model.member.YouzanMemberDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author jincai.wu
 * @date 2025/11/13
 */
@Mapper
public interface YouzanMemberDetailMapper {
    int insertSelective(YouzanMemberDetail record);

    YouzanMemberDetail selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(YouzanMemberDetail record);

    YouzanMemberDetail selectByAppIdAndMobile(@Param("appId") String appId, @Param("mobile") String mobile);

    YouzanMemberDetail selectByAppIdAndYzOpenId(@Param("appId") String appId, @Param("yzOpenId") String yzOpenId);
}
