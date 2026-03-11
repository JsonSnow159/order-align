package com.example.orderalign.mapper;

import com.example.orderalign.model.NotExistItem;
import org.apache.ibatis.annotations.Param;

/**
 * @author YOUR_NAME
 */
public interface NotExistItemMapper {

    /**
     * 插入
     * @param record
     * @return
     */
    int insert(NotExistItem record);

    /**
     * 根据kdtId、outItemNo、outSkuNo查询
     * @param kdtId
     * @param outItemNo
     * @param outSkuNo
     * @return
     */
    NotExistItem selectByKdtIdAndItemNoAndSkuNo(@Param("kdtId") Long kdtId, @Param("outItemNo") String outItemNo, @Param("outSkuNo") String outSkuNo);

    /**
     * 根据appId、outItemNo、outSkuNo查询
     * @param appId
     * @param outItemNo
     * @param outSkuNo
     * @return
     */
    NotExistItem selectByAppIdAndItemNoAndSkuNo(@Param("appId") String appId, @Param("outItemNo") String outItemNo, @Param("outSkuNo") String outSkuNo);
}
