package com.example.orderalign.model;

import lombok.Data;

@Data
public class KlsUser {
    private Long id;
    private String yzOpenId;
    private String mobile;
    private String outOpenId;
    // Add other fields from your kls_user table here
}
