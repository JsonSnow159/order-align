package com.example.orderalign.dto.kylin;

import com.alibaba.fastjson.annotation.JSONField;
import com.youzan.cloud.connector.sdk.client.BaseExtResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author:吴金才
 * @Date:2025/4/27 09:46
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KLSItemQueryResponse extends BaseExtResponse {
    /**
     * 大类名称
     */
    private String deptName;

    /**
     * 颜色
     */
    private String color;
    /**
     * 颜色编码
     */
    private String colorCode;

    /**
     * 渠道类型
     */
    private String channelType;

    /**
     * 商品名称
     */
    private String productName;

    /**
     * 同步时间
     */
    private String lastSync;

    /**
     * 是否特殊类型商品
     */
    private Boolean isSpecial;

    /**
     * 商品条码
     */
    private String eanCode;

    /**
     * 中类名称
     */
    private String familyName;

    /**
     * 季节
     */
    private String season;

    /**
     * 商品主键id
     */
    private String id;

    /**
     * 品牌
     */
    private String brand;

    /**
     * 上市年份
     */
    private String onSaleYear;

    /**
     * 中类编码
     */
    private String familyCode;

    /**
     * 是否有效(Y/N)
     */
    private String isValid;

    /**
     * 更新时间
     */
    private String updateTime;

    /**
     * 吊牌价
     */
    private Double tagPrice;

    /**
     * 商品批次编码
     */
    private String sqId;

    /**
     * 小类名称
     */
    private String subFamilyName;

    /**
     * 小类编码
     */
    private String subFamilyCode;

    /**
     * 商品描述
     */
    private String productDesc;

    /**
     * 商品编码
     */
    private String productCode;

    /**
     * 上市日期
     */
    private String onSaledate;

    /**
     * 材质
     */
    private String material;

    /**
     * 尺码
     */
    private String size;
    /**
     * 尺码编码
     */
    @JSONField(name = "sizecode")
    private String sizeCode;


    /**
     * 创建时间
     */
    private String createTime;

    /**
     * 会员类型
     */
    private String memberType;

    /**
     * 零售价
     */
    private Double retailPrice;

    /**
     * 大类编码
     */
    private String deptCode;
}
