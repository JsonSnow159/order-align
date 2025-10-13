package com.example.orderalign.mapper;

import com.example.orderalign.model.KaiLeShiRefundOrderAlignResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KaiLeShiRefundOrderAlignResultMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(KaiLeShiRefundOrderAlignResult record);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    KaiLeShiRefundOrderAlignResult selectByPrimaryKey(Long id);

    /**
     * 根据主键更新
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(KaiLeShiRefundOrderAlignResult record);

    /**
     * 根据主键删除
     * @param id
     * @return
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 根据refundId查询
     * @param refundId
     * @return
     */
    KaiLeShiRefundOrderAlignResult selectByRefundId(@Param("appId") String appId, @Param("refundId") String refundId);

}
