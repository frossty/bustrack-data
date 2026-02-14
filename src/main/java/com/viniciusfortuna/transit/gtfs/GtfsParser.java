package com.viniciusfortuna.transit.gtfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;

/**
 * Parses GTFS feed ZIP files into structured data.
 */
public class GtfsParser {

    public static GtfsSpec parseGtfsZip(InputStream gtfsFile) throws IOException {
        ZipInputStream zip = new ZipInputStream(gtfsFile);
        ArrayList<Agency> agencies = new ArrayList<>();
        ArrayList<Route> routes = new ArrayList<>();
        ArrayList<Stop> stops = new ArrayList<>();
        ArrayList<Trips> trips = new ArrayList<>();
        ArrayList<StopTimes> stopTimes = new ArrayList<>();
        ArrayList<Calendar> calendar = new ArrayList<>();
        ArrayList<CalendarDate> calendarDates = new ArrayList<>();

        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (entry.isDirectory()) {
                continue;
            }
            String filename = entry.getName();

            switch (filename) {
                case "agency.txt":
                    parseAgencies(zip, agencies);
                    break;
                case "stops.txt":
                    parseStops(zip, stops);
                    break;
                case "routes.txt":
                    parseRoutes(zip, routes);
                    break;
                case "trips.txt":
                    parseTrips(zip, trips);
                    break;
                case "stop_times.txt":
                    parseStopTimes(zip, stopTimes);
                    break;
                case "calendar.txt":
                    parseCalendar(zip, calendar);
                    break;
                case "calendar_dates.txt":
                    parseCalendarDates(zip, calendarDates);
                    break;
            }
        }

        System.out.printf("Parsed: %d agencies, %d stops, %d routes, %d trips, %d stop_times, %d calendar, %d calendar_dates%n",
                agencies.size(), stops.size(), routes.size(), trips.size(),
                stopTimes.size(), calendar.size(), calendarDates.size());

        return new GtfsSpec(agencies, stops, routes, trips, stopTimes, calendar, calendarDates);
    }

    private static void parseAgencies(InputStream stream, List<Agency> agencies) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            try {
                agencies.add(new Agency(
                        line.get("agency_id"),
                        line.get("agency_name"),
                        new URL(line.get("agency_url")),
                        TimeZone.getTimeZone(line.get("agency_timezone"))));
            } catch (Exception e) {
                System.err.println("Warning: skipping agency row: " + e.getMessage());
            }
        }
    }

    private static void parseStops(InputStream stream, List<Stop> stops) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            try {
                stops.add(new Stop(
                        line.get("stop_id"),
                        line.get("stop_name"),
                        Double.parseDouble(line.get("stop_lat")),
                        Double.parseDouble(line.get("stop_lon"))));
            } catch (Exception e) {
                System.err.println("Warning: skipping stop row: " + e.getMessage());
            }
        }
    }

    private static void parseRoutes(InputStream stream, List<Route> routes) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            try {
                routes.add(new Route(
                        line.get("route_id"),
                        line.get("route_short_name"),
                        line.get("route_long_name"),
                        Integer.parseInt(line.get("route_type"))));
            } catch (Exception e) {
                System.err.println("Warning: skipping route row: " + e.getMessage());
            }
        }
    }

    private static void parseTrips(InputStream stream, List<Trips> trips) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            trips.add(new Trips(
                    line.get("block_id"),
                    line.get("route_id"),
                    line.get("trip_headsign"),
                    line.get("service_id"),
                    line.get("shape_id"),
                    line.get("trip_id")));
        }
    }

    private static void parseStopTimes(InputStream stream, List<StopTimes> stopTimes) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            try {
                stopTimes.add(new StopTimes(
                        line.get("trip_id"),
                        line.get("arrival_time"),
                        line.get("departure_time"),
                        Integer.parseInt(line.get("stop_id")),
                        Integer.parseInt(line.get("stop_sequence")),
                        line.get("stop_headsign"),
                        line.get("pickup_type"),
                        line.get("drop_off_type"),
                        line.get("shape_dist_traveled")));
            } catch (Exception e) {
                System.err.println("Warning: skipping stop_time row: " + e.getMessage());
            }
        }
    }

    private static void parseCalendar(InputStream stream, List<Calendar> calendar) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            calendar.add(new Calendar(
                    line.get("service_id"),
                    line.get("start_date"),
                    line.get("end_date"),
                    Integer.parseInt(line.get("monday")),
                    Integer.parseInt(line.get("tuesday")),
                    Integer.parseInt(line.get("wednesday")),
                    Integer.parseInt(line.get("thursday")),
                    Integer.parseInt(line.get("friday")),
                    Integer.parseInt(line.get("saturday")),
                    Integer.parseInt(line.get("sunday"))));
        }
    }

    private static void parseCalendarDates(InputStream stream, List<CalendarDate> calendarDates) throws IOException {
        List<Map<String, String>> lines = readCsv(stream);
        for (Map<String, String> line : lines) {
            calendarDates.add(new CalendarDate(
                    line.get("service_id"),
                    line.get("date"),
                    Integer.parseInt(line.get("exception_type"))));
        }
    }

    private static List<Map<String, String>> readCsv(InputStream csv) throws IOException {
        CSVReader reader = new CSVReader(new InputStreamReader(csv));
        ArrayList<Map<String, String>> lines = new ArrayList<>();
        try {
            String[] columnNames = reader.readNext();
            if (columnNames == null) {
                return lines;
            }
            // Strip BOM from first column name if present
            if (columnNames.length > 0 && columnNames[0].startsWith("\uFEFF")) {
                columnNames[0] = columnNames[0].substring(1);
            }
            String[] line;
            while ((line = reader.readNext()) != null) {
                HashMap<String, String> map = new HashMap<>();
                for (int ci = 0; ci < line.length && ci < columnNames.length; ci++) {
                    map.put(columnNames[ci].trim(), line[ci].trim());
                }
                lines.add(map);
            }
        } catch (CsvValidationException e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        }
        return lines;
    }

    private GtfsParser() {}
}
