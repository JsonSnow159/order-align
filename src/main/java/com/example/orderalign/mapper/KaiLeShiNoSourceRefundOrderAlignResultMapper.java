package com.example.orderalign.mapper;

import com.example.orderalign.model.KaiLeShiNoSourceRefundOrderAlignResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
@Mapper
public interface KaiLeShiNoSourceRefundOrderAlignResultMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(KaiLeShiNoSourceRefundOrderAlignResult record);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    KaiLeShiNoSourceRefundOrderAlignResult selectByPrimaryKey(Long id);

    /**
     * 根据主键更新
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(KaiLeShiNoSourceRefundOrderAlignResult record);

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
    KaiLeShiNoSourceRefundOrderAlignResult selectByRefundId(@Param("appId") String appId, @Param("refundId") String refundId);

}
