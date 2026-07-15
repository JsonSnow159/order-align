package com.example.orderalign.model;

import lombok.Data;
import java.util.Date;

@Data
public class KlsOrder {
    private Long id;
    private String tid;
    private String outTid;
    private Integer status;
    private Date createdAt;
    private Date updatedAt;
}
