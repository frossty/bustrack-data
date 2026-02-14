package com.viniciusfortuna.transit.gtfs;

public class StopTimes {

	String trip_id;
	String arrival_time;
	String departure_time;
	int stop_id;
	int stop_sequence;
	String stop_headsign;
	String pickup_type;
	String drop_off_type;
	String shape_dist_traveled;
	
	public StopTimes(String trip_id, String arrival_time,
			String departure_time, int stop_id, int stop_sequence,
			String stop_headsign, String pickup_type, String drop_off_type,
			String shape_dist_traveled) {
		this.trip_id = trip_id;
		this.arrival_time = arrival_time;
		this.departure_time = departure_time;
		this.stop_id = stop_id;
		this.stop_sequence = stop_sequence;
		this.stop_headsign = stop_headsign;
		this.pickup_type = pickup_type;
		this.drop_off_type = drop_off_type;
		this.shape_dist_traveled = shape_dist_traveled;
	}

	public String getTrip_id() {
		return trip_id;
	}

	public void setTrip_id(String trip_id) {
		this.trip_id = trip_id;
	}
}
