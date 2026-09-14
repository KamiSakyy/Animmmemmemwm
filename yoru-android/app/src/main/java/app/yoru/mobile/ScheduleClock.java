package app.yoru.mobile;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

final class ScheduleClock {
    private static final Locale RU=new Locale("ru");
    private ScheduleClock(){}
    static ZoneId zone(boolean local){return local?ZoneId.systemDefault():ZoneId.of("Europe/Moscow");}
    static LocalDate date(long instant,ZoneId zone){return Instant.ofEpochMilli(instant).atZone(zone).toLocalDate();}
    static long dayStart(long instant,int offset,ZoneId zone){return date(instant,zone).plusDays(offset).atStartOfDay(zone).toInstant().toEpochMilli();}
    static int dayIndex(long base,long instant,ZoneId zone){return (int)ChronoUnit.DAYS.between(date(base,zone),date(instant,zone));}
    static String format(long instant,String pattern,ZoneId zone){return DateTimeFormatter.ofPattern(pattern,RU).format(Instant.ofEpochMilli(instant).atZone(zone));}
    static String time(long instant,ZoneId zone){String suffix=zone.getId().equals("Europe/Moscow")?"МСК":"UTC"+Instant.ofEpochMilli(instant).atZone(zone).getOffset().getId().replace("Z","+00:00");return format(instant,"HH:mm",zone)+" "+suffix;}
}
