package com.example.orderalign.mapper;

import com.example.orderalign.model.KlsUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KlsUserMapper {
    List<KlsUser> findAll();

    KlsUser selectByMobile(@Param("mobile") String mobile);

    KlsUser selectByYzOpenId(@Param("yzOpenId") String yzOpenId);

    KlsUser selectByOutOpenId(@Param("outOpenId") String outOpenId);
}
