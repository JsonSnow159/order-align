package com.example.orderalign.dto.member;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class MemberAlignDTO {
    private String appId;
    private Long rootKdtId;
    private String mobile;
    private List<String> mobileList;
}
