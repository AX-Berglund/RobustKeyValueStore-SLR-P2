package demo;

import java.io.Serializable;

public class LatencyReportMessage implements Serializable {
    public final double putLatency;
    public final double getLatency;

    public LatencyReportMessage(double putLatency, double getLatency) {
        this.putLatency = putLatency;
        this.getLatency = getLatency;
    }
}
