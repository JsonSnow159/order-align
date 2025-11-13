package com.example.orderalign.mapper.member;

import com.example.orderalign.model.member.ThirdPartyMemberDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author jincai.wu
 * @date 2025/11/13
 */
@Mapper
public interface ThirdPartyMemberDetailMapper {
    int insert(ThirdPartyMemberDetail record);

    ThirdPartyMemberDetail selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(ThirdPartyMemberDetail record);

    ThirdPartyMemberDetail selectByAppIdAndMobile(@Param("appId") String appId, @Param("mobile") String mobile);

    ThirdPartyMemberDetail selectByAppIdAndMemberId(@Param("appId") String appId, @Param("memberId") String memberId);
}
