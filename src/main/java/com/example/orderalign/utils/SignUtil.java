package com.example.orderalign.utils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * @Description: 麒麟加签处理工具类
 * @Author: ludan
 * @Date: 2023/2/14
 **/
public class SignUtil {
    /**
     * 获取麒麟 sign
     * @param callerService service，数云又叫账号
     * @param contextPath 被调用服务名称， 每个商家不同，配置值
     * @param version 接口版本
     * @param timestamp 请求时间，需要保持与header中一致
     * @param serviceSecret  调用秘钥，数云又叫密码
     * @param requestPath 请求路径
     * @return
     */
    public static String generateSign(String callerService, String contextPath, String version,
                                      String timestamp, String serviceSecret, String requestPath) {
        String sign = "";
        if (callerService == null || callerService.equals("") || contextPath == null ||
                contextPath.equals("") ||
                timestamp == null || timestamp.equals("") || serviceSecret == null ||
                serviceSecret.equals("")) {
            return sign;
        }
        Map<String, String> map = new LinkedHashMap<>();
        map.put("callerService", callerService);
        map.put("contextPath", contextPath);
        try {
            if (requestPath != null) {
                StringBuilder sb = new StringBuilder();
                for(String part : requestPath.split("/")) {
                    sb.append("/").append(URLEncoder.encode(part,"utf-8"));
                }
                map.put("requestPath", sb.toString().substring(1));
            }
            map.put("timestamp", timestamp);
            map.put("v", version);
            sign = generateMD5Sign(serviceSecret, map);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
            return "";
        }
        return sign;
    }

    private static String generateMD5Sign(String secret, Map<String, String> parameters)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] bytes = md5.digest(generateConcatSign(secret, parameters).getBytes("utf8"));
        return byteToHex(bytes);
    }

    private static String generateConcatSign(String secret, Map<String, String>
            parameters) {
        StringBuilder sb = new StringBuilder().append(secret);
        Set<String> keys = parameters.keySet();
        for (String key : keys) {
            sb.append(key).append(parameters.get(key));
        }
        return sb.append(secret).toString();
    }

    private static String byteToHex(byte[] bytesIn) {
        StringBuilder sb = new StringBuilder();
        for (byte byteIn : bytesIn) {
            String bt = Integer.toHexString(byteIn & 0xff);
            if (bt.length() == 1){
                sb.append(0).append(bt);
            }else{
                sb.append(bt);
            }
        }
        return sb.toString().toUpperCase();
    }
}
