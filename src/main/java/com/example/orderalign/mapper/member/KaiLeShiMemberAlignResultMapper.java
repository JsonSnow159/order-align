package com.example.orderalign.mapper.member;

import com.example.orderalign.model.member.KaiLeShiMemberAlignResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author jincai.wu
 * @date 2025/11/13
 */
@Mapper
public interface KaiLeShiMemberAlignResultMapper {
    int insertSelective(KaiLeShiMemberAlignResult record);

    int updateByPrimaryKeySelective(KaiLeShiMemberAlignResult record);
}
