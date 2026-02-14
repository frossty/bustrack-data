package com.viniciusfortuna.transit.gtfs;

import java.util.List;

/**
 * Holds an entire GTFS specification.
 * See https://developers.google.com/transit/gtfs/reference for details.
 *
 * @author Vinicius Fortuna
 */
public class GtfsSpec {
  List<Agency> agencies;
  List<Stop> stops;
  List<Route> routes;
  List<Trips> trips;
  List<StopTimes> stopTimes;
  List<Calendar> calendar;
  List<CalendarDate> calendarDates;

  GtfsSpec(List<Agency> agencies, List<Stop> stops, List<Route> routes,
           List<Trips> trips, List<StopTimes> stopTimes, List<Calendar> calendar,
           List<CalendarDate> calendarDates) {
    this.agencies = agencies;
    this.stops = stops;
    this.routes = routes;
    this.trips = trips;
    this.stopTimes = stopTimes;
    this.calendar = calendar;
    this.calendarDates = calendarDates;
  }
}
