package com.example.orderalign.utils;

import com.youzan.cloud.connector.sdk.common.utils.DateFormatUtil;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 凯乐石通用的util工具类
 *
 */
public class KaileshiUtil {

    /**
     * 将0时区的时间转换为东八区的时间，转换的格式为： yyyy-MM-dd HH:mm:ss
     * @param origin
     * @return
     */
    public static String convertTime2UTC8Util(String origin) {
        if (StringUtils.isEmpty(origin)) {
            return null;
        }
        Instant instant = Instant.parse(origin);
        ZoneId targetZone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime zonedDateTime = instant.atZone(targetZone);
        String formatter = zonedDateTime.format(DateTimeFormatter.ofPattern(DateFormatUtil.YYYY_MM_DD_HH_MM_SS));
        return formatter;
    }

    /**
     * 将0时区的时间转换为东八区的时间，转换的格式为： yyyy-MM-dd HH:mm:ss， 并返回Date类型
     * @param origin
     * @return
     */
    public static Date convertTime2UTC8DateUtil(String origin) {
        return Stream.of(convertTime2UTC8Util(origin)).filter(Objects::nonNull).map(DateFormatUtil::parseStr2Date).findFirst().orElse(null);
    }

    /**
     * 将东八区时区的时间转换为东八区的时间，转换的格式为： yyyy-MM-dd HH:mm:ss
     * @param origin
     * @return
     */
    public static String convertUTC8Time2UTC8Util(String origin) {
        if (StringUtils.isEmpty(origin)) {
            return null;
        }
        ZonedDateTime zonedDateTime = ZonedDateTime.parse(origin);
        // 定义目标格式（去掉时区信息）
        String formatter = zonedDateTime.format(DateTimeFormatter.ofPattern(DateFormatUtil.YYYY_MM_DD_HH_MM_SS));
        return formatter;
    }

    /**
     * 将东八区时区的时间转换为东八区的时间，转换的格式为： yyyy-MM-dd HH:mm:ss， 并返回Date类型
     * @param origin
     * @return
     */
    public static Date convertUTC8Time2UTC8DateUtil(String origin) {
        return Stream.of(convertUTC8Time2UTC8Util(origin)).filter(Objects::nonNull).map(DateFormatUtil::parseStr2Date).findFirst().orElse(null);
    }

    public static Date convertTime2UTC8DateUtilOffset(Date originalDate) {

        // 创建一个 Calendar 对象，并设置为原始日期
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(originalDate);

        // 将时间设置为第二天的 00:00:00
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // 获取修改后的日期
        Date modifiedDate = calendar.getTime();
        return modifiedDate;
    }

    /**
     * 将double类型元的价格转为long类型的分
     * @param price
     * @return
     */
    public static Long parseDoublePrice2Fen(Double price) {
        if (price == null) {
            return 0L;
        }
        return price.longValue() * 100;
    }

    public static Long handlePrice(Double total,Integer num) {
       return new BigDecimal(total).divide(new BigDecimal(num),2, RoundingMode.HALF_UP).multiply(new BigDecimal(100)).longValue();
    }
}
