package com.silibrina.tecnova.opendata.models;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;


public class EntryMetadataPresenter {

    public static Date getCalMax(Date maxDate) throws ParseException {
        Calendar calMaxDate = getCalendarDate(maxDate);
        calMaxDate.set(Calendar.HOUR_OF_DAY, 23);
        calMaxDate.set(Calendar.MINUTE, 59);
        calMaxDate.set(Calendar.SECOND, 59);
        calMaxDate.set(Calendar.MILLISECOND, 0);
        return calMaxDate.getTime();
    }

    public static Date getCalMin(Date minDate) throws ParseException {
        Calendar calMinDate = getCalendarDate(minDate);
        calMinDate.set(Calendar.HOUR_OF_DAY, 0);
        calMinDate.set(Calendar.MINUTE, 0);
        calMinDate.set(Calendar.SECOND, 0);
        calMinDate.set(Calendar.MILLISECOND, 0);
        return calMinDate.getTime();
    }

    private static Calendar getCalendarDate(Date dateSource) throws ParseException {
        Calendar dateCalendar = Calendar.getInstance();
        dateCalendar.setTime(dateSource);
        return dateCalendar;
    }

}
