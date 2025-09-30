package com.example.orderalign.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MemberFixDTO {
    private String appId;
    private Long rootKdtId;
    private String mobile;
    //批次号，用来拼接做幂等键
    private Long batchId;
}