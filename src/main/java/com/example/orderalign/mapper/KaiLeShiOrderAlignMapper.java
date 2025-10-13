package com.example.orderalign.mapper;

import com.example.orderalign.model.KaiLeShiOrderAlign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author jincai.wu
 * @date 2025/9/18
 */
@Mapper
public interface KaiLeShiOrderAlignMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(KaiLeShiOrderAlign record);

    /**
     * 根据appId和outTid查询
     * @param appId
     * @param outTid
     * @return
     */
    KaiLeShiOrderAlign selectByAppIdAndOutTid(@Param("appId") String appId, @Param("outTid") String outTid);

    /**
     * 根据状态查询
     * @param status
     * @param limit
     * @return
     */
    List<KaiLeShiOrderAlign> selectByStatusWithLimit(@Param("appId") String appId, @Param("status") Integer status, @Param("limit") int limit);

    /**
     * 更新
     * @param record
     * @return
     */
    int update(KaiLeShiOrderAlign record);

}