package com.atakmap.android.scythe.model;

import java.util.List;

/** Represents heatmap data returned by rf_scythe_api_server. */
public class HeatmapData {

    /** A single weighted point on the heatmap. */
    public static class Point {
        private double latitude;
        private double longitude;
        private double weight;

        public Point() {}

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }
    }

    private String missionId;
    private List<Point> points;
    private String generatedAt;

    public HeatmapData() {}

    public String getMissionId() {
        return missionId;
    }

    public void setMissionId(String missionId) {
        this.missionId = missionId;
    }

    public List<Point> getPoints() {
        return points;
    }

    public void setPoints(List<Point> points) {
        this.points = points;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }
}
