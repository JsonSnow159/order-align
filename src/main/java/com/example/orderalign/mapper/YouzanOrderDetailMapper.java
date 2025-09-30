package com.example.orderalign.mapper;

import com.example.orderalign.model.YouzanOrderDetail;
import org.apache.ibatis.annotations.Param;

/**
 * @author jincai.wu
 * @date 2025/9/22
 */
public interface YouzanOrderDetailMapper {

    /**
     * 新增
     * @param record
     * @return
     */
    int insert(YouzanOrderDetail record);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    YouzanOrderDetail selectByPrimaryKey(Long id);

    /**
     * 根据主键更新
     * @param record
     * @return
     */
    int updateByPrimaryKeySelective(YouzanOrderDetail record);

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
    YouzanOrderDetail selectByTid(@Param("tid") String tid);

}
