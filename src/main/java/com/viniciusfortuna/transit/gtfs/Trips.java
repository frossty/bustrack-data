package com.viniciusfortuna.transit.gtfs;

public class Trips {

	String block_id;
	String route_id;
	String trip_headsign;
	String service_id;
	String shape_id;
	String trip_id;
	
	public Trips(String block_id, String route_id, String trip_headsign,
			String service_id, String shape_id, String trip_id) {

		this.block_id = block_id;
		this.route_id = route_id;
		this.trip_headsign = trip_headsign;
		this.service_id = service_id;
		this.shape_id = shape_id;
		this.trip_id = trip_id;
	}

	public String getTrip_id() {
		return trip_id;
	}

	public void setTrip_id(String trip_id) {
		this.trip_id = trip_id;
	}

}
