package demo;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.List;

public class Aggregator extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private List<Double> putLatencies = new ArrayList<>();
    private List<Double> getLatencies = new ArrayList<>();
    private int expectedReports; // Total number of processes
    private int receivedReports = 0;

    public Aggregator(int expectedReports) {
        this.expectedReports = expectedReports;
    }

    public static Props createActor(int expectedReports) {
        return Props.create(Aggregator.class, () -> new Aggregator(expectedReports));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(LatencyReportMessage.class, this::onLatencyReport)
            .build();
    }

    private void onLatencyReport(LatencyReportMessage msg) {
        putLatencies.add(msg.putLatency);
        getLatencies.add(msg.getLatency);
        receivedReports++;

        log.info("[Aggregator] Received latency report (PUT: {} ms, GET: {} ms).", 
            msg.putLatency, msg.getLatency);

        if (receivedReports >= expectedReports) {
            computeAndLogAverage();
        }
    }

    private void computeAndLogAverage() {
        double avgPutLatency = putLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgGetLatency = getLatencies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        log.info("[Aggregator] FINAL AVERAGE LATENCIES:");
        log.info("[Aggregator] Average PUT latency: {} ms", String.format("%.2f", avgPutLatency));
        log.info("[Aggregator] Average GET latency: {} ms", String.format("%.2f", avgGetLatency));
    }
}
