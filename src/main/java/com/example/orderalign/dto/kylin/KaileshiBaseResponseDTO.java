package com.example.orderalign.dto.kylin;

import lombok.Data;

import java.io.Serializable;

/**
 * 凯乐石响应基础对象
 */
@Data
public class KaileshiBaseResponseDTO<T> implements Serializable {

    /**
     * 业务上返回结果是否成功，一般失败为false
     */
    private Boolean success;

    private T data;
    /**
     * 接口错误信息， 一般错误有值
     */
    private String msg;
}
