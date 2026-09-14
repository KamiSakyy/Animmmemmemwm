package app.yoru.mobile;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class ScheduleClockTest {
    @Test public void moscowTimeHasExplicitSuffix(){
        long instant=Instant.parse("2026-09-14T15:00:00Z").toEpochMilli();
        assertEquals("18:00 МСК",ScheduleClock.time(instant,ZoneId.of("Europe/Moscow")));
    }
    @Test public void springTransitionUsesCalendarDays(){
        ZoneId zone=ZoneId.of("Europe/Warsaw");long base=Instant.parse("2026-03-29T00:00:00Z").toEpochMilli();
        long start=ScheduleClock.dayStart(base,0,zone),next=ScheduleClock.dayStart(base,1,zone);
        assertEquals(23*3600000L,next-start);assertEquals(1,ScheduleClock.dayIndex(start,next,zone));
    }
    @Test public void autumnTransitionUsesCalendarDays(){
        ZoneId zone=ZoneId.of("Europe/Warsaw");long base=Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();
        long start=ScheduleClock.dayStart(base,0,zone),next=ScheduleClock.dayStart(base,1,zone);
        assertEquals(25*3600000L,next-start);assertEquals(1,ScheduleClock.dayIndex(start,next,zone));
    }
    @Test public void localSuffixUsesOffsetAtEventTime(){
        assertEquals("17:00 UTC+02:00",ScheduleClock.time(Instant.parse("2026-09-14T15:00:00Z").toEpochMilli(),ZoneId.of("Europe/Warsaw")));
    }
}
