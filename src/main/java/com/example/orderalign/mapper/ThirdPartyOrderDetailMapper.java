package com.example.orderalign.mapper;

import com.example.orderalign.model.ThirdPartyOrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author jincai.wu
 * @date 2025/9/22
 */
@Mapper
public interface ThirdPartyOrderDetailMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(ThirdPartyOrderDetail record);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    ThirdPartyOrderDetail selectByPrimaryKey(Long id);

    /**
     * 根据主键更新
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(ThirdPartyOrderDetail record);

    /**
     * 根据主键删除
     * @param id
     * @return
     */
    int deleteByPrimaryKey(Long id);

    /**
     * 根据outTid查询
     * @param outTid
     * @return
     */
    ThirdPartyOrderDetail selectByOutTid(@Param("outTid") String outTid);

}
