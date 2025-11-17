package com.example.orderalign.mapper.member;

import com.example.orderalign.model.member.KaiLeShiMemberAlign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author jincai.wu
 * @date 2025/11/13
 */
@Mapper
public interface KaiLeShiMemberAlignMapper {

    int insert(KaiLeShiMemberAlign record);

    KaiLeShiMemberAlign selectByPrimaryKey(Long id);

    int update(KaiLeShiMemberAlign record);

    KaiLeShiMemberAlign selectByAppIdAndMobile(@Param("appId") String appId, @Param("mobile") String mobile);

    KaiLeShiMemberAlign selectByAppIdAndYzOpenId(@Param("appId") String appId, @Param("yzOpenId") String yzOpenId);

    KaiLeShiMemberAlign selectByAppIdAndMemberId(@Param("appId") String appId, @Param("memberId") String memberId);

    int delete(Long id);

    List<KaiLeShiMemberAlign> selectByStatus(@Param("status") Integer status);

    List<KaiLeShiMemberAlign> selectByStatusWithLimit(@Param("status") Integer status, @Param("limit") int limit);
}
