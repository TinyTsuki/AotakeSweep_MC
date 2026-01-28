package xin.vanilla.aotake.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class DateUtils {

    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";


    public DateUtils() {
    }

    private static Date formatEx(String dateStr, String pattern) {
        if (StringUtils.isNullOrEmpty(dateStr)) {
            return null;
        } else {
            try {
                return (new SimpleDateFormat(pattern)).parse(dateStr);
            } catch (ParseException e) {
                return null;
            }
        }
    }

    private static List<String> getStrings(String pattern) {
        List<String> formats = new ArrayList<>();
        if (!StringUtils.isNullOrEmpty(pattern)) {
            formats.add(pattern);
        } else {
            formats.add("HH:mm:ss");
            formats.add("yyyy");
            formats.add("yyyyMM");
            formats.add("yyyyMMdd");
            formats.add("yyyy-MM-dd");
            formats.add("yyyy-MM-dd HH:mm");
            formats.add("yyyy-MM-dd HH:mm:ss");
            formats.add("yyyy年MM月dd日");
            formats.add("yyyy/MM/ddHHmm");
            formats.add("yyyy/MM/dd");
            formats.add("yyyyMMddHHmmss");
            formats.add("yyyy.MM.dd");
        }
        return formats;
    }

    public static Date format(String strTime) {
        return format(strTime, null);
    }

    public static Date format(String strTime, String pattern) {
        if (StringUtils.isNullOrEmpty(strTime)) {
            return null;
        } else {
            Date date = null;
            List<String> formats = getStrings(pattern);
            for (String format : formats) {
                if ((strTime.indexOf("-") <= 0 || format.contains("-")) && (strTime.contains("-") || format.indexOf("-") <= 0) && strTime.length() <= format.length()) {
                    date = formatEx(strTime, format);
                    if (date != null) {
                        break;
                    }
                }
            }
            return date;
        }
    }

    public static String toString(Date date) {
        return toString(date, DATE_FORMAT);
    }

    public static String toDateTimeString(Date date) {
        return toString(date, DATETIME_FORMAT);
    }

    public static String toString(Date date, String pattern) {
        if (date == null) date = new Date();
        SimpleDateFormat format = new SimpleDateFormat(pattern);
        return format.format(date);
    }

    public static Date addSecond(Date current, int second) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.SECOND, second);
        return calendar.getTime();
    }

    public static Date addMilliSecond(Date current, int ms) {
        if (current == null) {
            current = new Date();
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current);
        calendar.add(Calendar.MILLISECOND, ms);
        return calendar.getTime();
    }

    public static LocalDateTime getLocalDateTime(Date date) {
        if (date == null) {
            date = new Date();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * 计算两个时间之间的秒数间隔
     */
    public static long secondsOfTwo(Date startDateTime, Date endDateTime) {
        return ChronoUnit.SECONDS.between(getLocalDateTime(startDateTime), getLocalDateTime(endDateTime));
    }

}
