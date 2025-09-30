package com.example.orderalign.controller;

import com.example.orderalign.mapper.KlsUserMapper;
import com.example.orderalign.model.KlsUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class KlsUserController {

    private final KlsUserMapper klsUserMapper;

    @Autowired
    public KlsUserController(KlsUserMapper klsUserMapper) {
        this.klsUserMapper = klsUserMapper;
    }

    @GetMapping
    public List<KlsUser> getAllUsers() {
        return klsUserMapper.findAll();
    }
}
