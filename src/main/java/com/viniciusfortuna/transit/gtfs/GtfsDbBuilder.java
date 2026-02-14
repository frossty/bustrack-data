package com.viniciusfortuna.transit.gtfs;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Main pipeline: download GTFS feed -> parse -> create SQLite DB -> compress -> hash -> manifest.
 */
public class GtfsDbBuilder {

    private static final String GTFS_URL = "https://gtfs.halifax.ca/static/google_transit.zip";
    private static final String DB_FILENAME = "busTrack.db";
    private static final String GZ_FILENAME = "busTrack.db.gz";
    private static final String MANIFEST_FILENAME = "version.json";
    private static final int DEFAULT_DB_VERSION = 28;

    public static void main(String[] args) throws Exception {
        String localFile = null;
        String outputDir = "output";
        int dbVersion = DEFAULT_DB_VERSION;

        // Parse command-line arguments
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--local":
                    if (i + 1 < args.length) localFile = args[++i];
                    break;
                case "--output":
                    if (i + 1 < args.length) outputDir = args[++i];
                    break;
                case "--version":
                    if (i + 1 < args.length) dbVersion = Integer.parseInt(args[++i]);
                    break;
            }
        }

        Path outPath = Paths.get(outputDir);
        Files.createDirectories(outPath);

        // Step 1: Get GTFS feed
        Path gtfsZipPath;
        if (localFile != null) {
            gtfsZipPath = Paths.get(localFile);
            System.out.println("Using local GTFS file: " + gtfsZipPath);
        } else {
            gtfsZipPath = outPath.resolve("gtfs.zip");
            downloadGtfsFeed(gtfsZipPath);
        }

        // Step 2: Parse GTFS
        System.out.println("Parsing GTFS feed...");
        GtfsSpec gtfs;
        try (InputStream is = new FileInputStream(gtfsZipPath.toFile())) {
            gtfs = GtfsParser.parseGtfsZip(is);
        }

        // Step 3: Create SQLite DB
        Path dbPath = outPath.resolve(DB_FILENAME);
        Files.deleteIfExists(dbPath);
        System.out.println("Creating SQLite database: " + dbPath);
        createDatabase(dbPath, gtfs, dbVersion);

        // Step 4: Compress
        Path gzPath = outPath.resolve(GZ_FILENAME);
        System.out.println("Compressing database...");
        gzipFile(dbPath, gzPath);

        // Step 5: Hash
        String sha256 = computeSha256(gzPath);
        System.out.println("SHA-256: " + sha256);

        // Step 6: Generate version manifest
        Path manifestPath = outPath.resolve(MANIFEST_FILENAME);
        generateManifest(manifestPath, dbVersion, gzPath.toFile().length(), sha256);

        System.out.println();
        System.out.println("=== Build complete ===");
        System.out.println("  Database:  " + dbPath);
        System.out.println("  Compressed: " + gzPath + " (" + gzPath.toFile().length() + " bytes)");
        System.out.println("  Manifest:  " + manifestPath);
    }

    private static void downloadGtfsFeed(Path destination) throws IOException {
        System.out.println("Downloading GTFS feed from " + GTFS_URL + "...");
        URL url = new URL(GTFS_URL);
        try (InputStream in = url.openStream()) {
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("Downloaded: " + destination + " (" + destination.toFile().length() + " bytes)");
    }

    private static void createDatabase(Path dbPath, GtfsSpec gtfs, int dbVersion) throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + dbPath.toString();
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                // Create schema
                createSchema(stmt);

                // Set DB version
                stmt.execute("PRAGMA user_version = " + dbVersion + ";");
            }

            // Insert data with batch operations
            conn.setAutoCommit(false);

            insertStops(conn, gtfs);
            insertRoutes(conn, gtfs);
            insertTrips(conn, gtfs);
            insertStopTimes(conn, gtfs);
            insertCalendar(conn, gtfs);
            insertCalendarDates(conn, gtfs);
            insertAndroidMetadata(conn);

            conn.commit();
            System.out.println("Database created successfully.");
        }
    }

    private static void createSchema(Statement stmt) throws SQLException {
        // GTFS tables
        stmt.execute("CREATE TABLE stops(_id INTEGER PRIMARY KEY, name varchar(100), latt REAL, log REAL)");
        stmt.execute("CREATE TABLE routes(_id INTEGER PRIMARY KEY AUTOINCREMENT, route_id varchar(20), short_name varchar(20), long_name varchar(100), type varchar(20))");
        stmt.execute("CREATE TABLE trips(_id INTEGER PRIMARY KEY AUTOINCREMENT, block_id varchar(60), route_id varchar(60), trip_headsign varchar(60), service_id varchar(60), shape_id varchar(60), trip_id varchar(50) NOT NULL)");
        stmt.execute("CREATE TABLE stopTimes(_id INTEGER PRIMARY KEY AUTOINCREMENT, trip_id varchar(50) NOT NULL, arrival_time varchar(10), departure_time varchar(10), stop_id int, stop_sequence int, stop_headsign varchar(10), pickup_type varchar(10), drop_off_type varchar(10), shape_dist_traveled varchar(10))");
        stmt.execute("CREATE TABLE calendar(_id INTEGER PRIMARY KEY AUTOINCREMENT, service_id varchar(60), start_date varchar(60), end_date varchar(60), monday INTEGER(0), tuesday INTEGER(0), wednesday INTEGER(0), thursday INTEGER(0), friday INTEGER(0), saturday INTEGER(0), sunday INTEGER(0))");
        stmt.execute("CREATE TABLE calendar_dates(service_id TEXT, date NUMERIC, exception_type NUMERIC)");

        // User data table (created empty)
        stmt.execute("CREATE TABLE settings(_id INTEGER PRIMARY KEY, fieldname varchar(25), value1 varchar(50), value2 varchar(50), value3 varchar(50), checkInTime int DEFAULT (strftime('%s','now')), status NUMERIC NOT NULL DEFAULT ('1'), value4 VARCHAR(50), value5 VARCHAR(50), value6 VARCHAR(50))");

        // Android metadata
        stmt.execute("CREATE TABLE android_metadata(locale TEXT)");

        // Indexes
        stmt.execute("CREATE INDEX trip_id ON trips(trip_id)");
        stmt.execute("CREATE INDEX strip_id ON stopTimes(trip_id)");

        // Views
        stmt.execute("CREATE VIEW valid_service_ids AS SELECT service_id FROM calendar_dates WHERE date == strftime('%Y%m%d', 'now', 'localtime')");
    }

    private static void insertStops(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.stops.size() + " stops...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stops(_id, name, latt, log) VALUES(?, ?, ?, ?)")) {
            int count = 0;
            for (Stop s : gtfs.stops) {
                ps.setString(1, s.id);
                ps.setString(2, s.name);
                ps.setDouble(3, s.latitude);
                ps.setDouble(4, s.longitude);
                ps.addBatch();
                if (++count % 10000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private static void insertRoutes(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.routes.size() + " routes...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO routes(route_id, short_name, long_name, type) VALUES(?, ?, ?, ?)")) {
            for (Route r : gtfs.routes) {
                ps.setString(1, r.id);
                ps.setString(2, r.shortName);
                ps.setString(3, r.longName);
                ps.setInt(4, r.type);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertTrips(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.trips.size() + " trips...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO trips(route_id, trip_headsign, service_id, trip_id) VALUES(?, ?, ?, ?)")) {
            int count = 0;
            for (Trips t : gtfs.trips) {
                ps.setString(1, t.route_id);
                ps.setString(2, t.trip_headsign);
                ps.setString(3, t.service_id);
                ps.setString(4, t.trip_id);
                ps.addBatch();
                if (++count % 10000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private static void insertStopTimes(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.stopTimes.size() + " stop_times...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stopTimes(trip_id, arrival_time, departure_time, stop_id, stop_sequence, stop_headsign, pickup_type, drop_off_type, shape_dist_traveled) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int count = 0;
            for (StopTimes st : gtfs.stopTimes) {
                ps.setString(1, st.trip_id);
                ps.setString(2, st.arrival_time);
                ps.setString(3, st.departure_time);
                ps.setInt(4, st.stop_id);
                ps.setInt(5, st.stop_sequence);
                ps.setString(6, st.stop_headsign);
                ps.setString(7, st.pickup_type);
                ps.setString(8, st.drop_off_type);
                ps.setString(9, st.shape_dist_traveled);
                ps.addBatch();
                if (++count % 10000 == 0) {
                    ps.executeBatch();
                    System.out.printf("  ... %,d stop_times inserted%n", count);
                }
            }
            ps.executeBatch();
        }
    }

    private static void insertCalendar(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.calendar.size() + " calendar entries...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO calendar(service_id, start_date, end_date, monday, tuesday, wednesday, thursday, friday, saturday, sunday) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (Calendar c : gtfs.calendar) {
                ps.setString(1, c.service_id);
                ps.setString(2, c.start_date);
                ps.setString(3, c.end_date);
                ps.setInt(4, c.monday);
                ps.setInt(5, c.tuesday);
                ps.setInt(6, c.wednesday);
                ps.setInt(7, c.thursday);
                ps.setInt(8, c.friday);
                ps.setInt(9, c.saturday);
                ps.setInt(10, c.sunday);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertCalendarDates(Connection conn, GtfsSpec gtfs) throws SQLException {
        System.out.println("Inserting " + gtfs.calendarDates.size() + " calendar_dates...");
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO calendar_dates(service_id, date, exception_type) VALUES(?, ?, ?)")) {
            for (CalendarDate cd : gtfs.calendarDates) {
                ps.setString(1, cd.service_id);
                ps.setString(2, cd.date);
                ps.setInt(3, cd.exception_type);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertAndroidMetadata(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO android_metadata(locale) VALUES(?)")) {
            ps.setString(1, "en_US");
            ps.execute();
        }
    }

    private static void gzipFile(Path source, Path destination) throws IOException {
        try (FileInputStream fis = new FileInputStream(source.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(new FileOutputStream(destination.toFile()))) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                gzos.write(buffer, 0, len);
            }
        }
        long rawSize = source.toFile().length();
        long gzSize = destination.toFile().length();
        double ratio = (1.0 - (double) gzSize / rawSize) * 100;
        System.out.printf("Compressed %,d bytes -> %,d bytes (%.1f%% reduction)%n", rawSize, gzSize, ratio);
    }

    private static String computeSha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                digest.update(buffer, 0, len);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void generateManifest(Path manifestPath, int dbVersion, long dbSizeBytes, String sha256) throws IOException {
        Date now = new Date();
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(now);
        String monthName = new SimpleDateFormat("MMMM", Locale.ENGLISH).format(now);
        String year = new SimpleDateFormat("yyyy").format(now);

        VersionManifest manifest = new VersionManifest();
        manifest.db_version = dbVersion;
        manifest.db_url = "<to be filled with GitHub Release URL>";
        manifest.db_size_bytes = dbSizeBytes;
        manifest.db_sha256 = sha256;
        manifest.gtfs_feed_date = dateStr;
        manifest.min_app_version = 57;
        manifest.changelog = monthName + " " + year + " schedule update";

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String json = gson.toJson(manifest);
        try (FileWriter writer = new FileWriter(manifestPath.toFile())) {
            writer.write(json);
        }
        System.out.println("Version manifest written: " + manifestPath);
    }

    private static class VersionManifest {
        int db_version;
        String db_url;
        long db_size_bytes;
        String db_sha256;
        String gtfs_feed_date;
        int min_app_version;
        String changelog;
    }
}
