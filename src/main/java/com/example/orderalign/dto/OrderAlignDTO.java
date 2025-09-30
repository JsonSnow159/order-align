package com.example.orderalign.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class OrderAlignDTO {
    private String appId;
    private Long rootKdtId;
    private String outTid;
    private List<String> outTidList;
}
