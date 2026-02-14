package com.viniciusfortuna.transit.gtfs;

public class CalendarDate {
    public String service_id;
    public String date;
    public int exception_type;

    public CalendarDate(String service_id, String date, int exception_type) {
        this.service_id = service_id;
        this.date = date;
        this.exception_type = exception_type;
    }
}
