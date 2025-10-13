package com.example.orderalign.mapper;

import com.example.orderalign.model.KaiLeShiOrderRefundAlign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KaiLeShiOrderRefundAlignMapper {

    int insert(KaiLeShiOrderRefundAlign record);

    KaiLeShiOrderRefundAlign selectByAppIdAndOutRefundId(@Param("appId") String appId, @Param("outRefundId") String outRefundId);

    List<KaiLeShiOrderRefundAlign> selectByStatusWithLimit(@Param("appId") String appId, @Param("status") Integer status, @Param("limit") int limit);

    int update(KaiLeShiOrderRefundAlign record);
}
