package com.example.myapplication.model;

/**
 * One turn of the tide - the moment the water stops rising and starts falling,
 * or the other way round.
 */
public class Tide {
    public enum Type { HIGH, LOW }

    private final Type type;
    private final long timeMillis;
    private final double heightFeet; // above mean lower low water, the datum tide tables use

    public Tide(Type type, long timeMillis, double heightFeet) {
        this.type = type;
        this.timeMillis = timeMillis;
        this.heightFeet = heightFeet;
    }

    public Type getType() { return type; }
    public long getTimeMillis() { return timeMillis; }
    public double getHeightFeet() { return heightFeet; }
}
