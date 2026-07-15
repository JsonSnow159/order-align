package com.example.orderalign.mapper;

import com.example.orderalign.model.KlsOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KlsOrderMapper {
    List<KlsOrder> selectByStatusAndIdGreaterThan(@Param("status") Integer status, @Param("lastId") Long lastId, @Param("limit") Integer limit);
}
