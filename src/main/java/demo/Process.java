package demo;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private final Map<Integer, Integer> localStore = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> timestamps = new ConcurrentHashMap<>();
    private boolean isCrashed = false;
    private final List<ActorRef> peers; // List of all processes

    public Process(List<ActorRef> peers) {
        this.peers = peers;
    }

    public static Props createActor(List<ActorRef> peers) {
        return Props.create(Process.class, () -> new Process(peers));
    }

    @Override
    public void onReceive(Object message) {
        if (isCrashed) return;

        if (message instanceof PutMessage) {
            PutMessage msg = (PutMessage) message;
            log.info("Received PUT for key: {} value: {}", msg.key, msg.value);
            if (msg.timestamp >= timestamps.getOrDefault(msg.key, 0)) {
                localStore.put(msg.key, msg.value);
                timestamps.put(msg.key, msg.timestamp);
                broadcast(msg); // Inform peers of the new value
            }
        } else if (message instanceof GetMessage) {
            GetMessage msg = (GetMessage) message;
            log.info("Received GET for key: {}", msg.key);
            aggregateGetResponse(msg);
        } else if (message instanceof CrashMessage) {
            isCrashed = true;
            log.info("Process crashed");
        } else if (message instanceof LaunchMessage) {
            log.info("Launching process operations...");
        } else {
            unhandled(message);
        }
    }

    private void aggregateGetResponse(GetMessage msg) {
        // Simulate querying majority (quorum)
        Map<Integer, Integer> responseTimestamps = new HashMap<>();
        for (ActorRef peer : peers) {
            peer.tell(msg, getSelf());
        }
        // Assume responses are collected and processed here
        int value = localStore.getOrDefault(msg.key, -1);
        log.info("Final value for key {}: {}", msg.key, value);
    }


    private void broadcast(PutMessage msg) {
        for (ActorRef peer : peers) {
            peer.tell(msg, getSelf());
        }
    }

    @Override
    public void postStop() {
        log.info("Process shutting down.");
    }

}