package com.example.orderalign.mapper;

import com.example.orderalign.model.KaiLeShiOrderAlignResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author jincai.wu
 * @date 2025/9/19
 */
@Mapper
public interface KaiLeShiOrderAlignResultMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(KaiLeShiOrderAlignResult record);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    KaiLeShiOrderAlignResult selectByPrimaryKey(Long id);

    /**
     * 根据主键更新
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(KaiLeShiOrderAlignResult record);

    /**
     * 根据主键删除
     * @param id
     * @return
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 根据tid查询
     * @param tid
     * @return
     */
    KaiLeShiOrderAlignResult selectByTid(@Param("appId") String appId, @Param("tid") String tid);

}
