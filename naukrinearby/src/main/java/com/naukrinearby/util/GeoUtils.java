package com.naukrinearby.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public final class GeoUtils {

	public static final int SRID_WGS84 = 4326;

	private static final GeometryFactory FACTORY =
			new GeometryFactory(new PrecisionModel(), SRID_WGS84);

	private GeoUtils() {
	}

	/** Builds a WGS84 point. Note: GIS order is (x=longitude, y=latitude). */
	public static Point point(double lat, double lng) {
		Point p = FACTORY.createPoint(new Coordinate(lng, lat));
		p.setSRID(SRID_WGS84);
		return p;
	}

	public static double latOf(Point p) {
		return p.getY();
	}

	public static double lngOf(Point p) {
		return p.getX();
	}
}
