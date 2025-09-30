package com.example.orderalign.mapper;

import com.example.orderalign.model.KlsUser;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface KlsUserMapper {
    List<KlsUser> findAll();
}
