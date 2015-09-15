/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 *
 * @author teofil
 */
public class TimeUtils {
    private static final DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter monthDayFormatter = DateTimeFormatter.ofPattern("MM.dd");
    private static final DateTimeFormatter fullDateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    
    
    private TimeUtils() {}
    
    public static final String formatDateRange(LocalDateTime start, LocalDateTime end) {
        if(start.getYear() == end.getYear() && start.getMonthValue() == end.getMonthValue() && start.getDayOfMonth() == end.getDayOfMonth()) {
            return fullDateFormatter.format(start);
        }
        else if(start.getYear() == end.getYear() && start.getMonthValue() == end.getMonthValue()) {
            return fullDateFormatter.format(start)+"-"+dayFormatter.format(end);
        }
        else if(start.getYear() == end.getYear()) {
            return fullDateFormatter.format(start)+"-"+monthDayFormatter.format(end);
        }
        else {
            return fullDateFormatter.format(start)+"-"+fullDateFormatter.format(end);
        }
    }
}
