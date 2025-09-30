package com.example.orderalign.dto.kylin;

import com.youzan.cloud.connector.sdk.client.BaseExtRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KLSItemQueryRequest extends BaseExtRequest {
    /**
     * 必填， 会员类型，常亮 kaileshi
     */
    private String memberType;

    /**
     * 商品code
     */
    private String productCode;

    /**
     * 商品批次编码
     */
    private String sqId;

    /**
     * 创建搜索开始时间必填
     */
    private String createBeginTime;

    /**
     * 创建搜索结束时间必填
     */
    private String  createEndTime;

    /**
     * 页码，必填
     */
    private Integer pageNo;

    /**
     * 分页条数，必填
     */
    private Integer pageSize;

}
