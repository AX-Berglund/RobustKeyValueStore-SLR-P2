package demo;

import java.util.ArrayList;
import java.util.List;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import demo.ABDMessages.ReadPhaseRequest;
import demo.ABDMessages.ReadPhaseResponse;
import demo.ABDMessages.WritePhaseAck;
import demo.ABDMessages.WritePhaseRequest;

public class Process extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    // Crash flag
    private boolean isCrashed = false;

    // Local single-key register
    private int localValue = 0;
    private int localTS = 0;

    // All peers (including self)
    private List<ActorRef> peers = new ArrayList<>();
    private int N;         
    private int quorumSize; 

    // Sequence number for the next read/write operation we initiate
    private int seqCounter = 0;

    private Operation currentOp = null; 

    private static class Operation {
        enum Type { PUT, GET }
        public Type type;
        public int seqNum;
        public int requestedValue; 
        public int maxTS;         
        public int maxValue;      
        public int responseCount; 
        public int needed;        
        public long startTime;    
        public Operation(Type type, int seqNum, int needed) {
            this.type = type;
            this.seqNum = seqNum;
            this.needed = needed;
        }
    }

    // For demonstration, each process has an index (processName) so we know who is who
    private final int processId;
    private final String processName;

    // For the sequential M puts + M gets scenario
    private static final int M = 3;
    private int putCount = 0;
    private int getCount = 0;

    public Process(List<ActorRef> initialPeers) {
        // We'll set peers after we get an InitPeersMessage, 
        // but let's store what we have for now if any.
        this.peers = initialPeers;
        this.processName = getSelf().path().name(); // get the name of the actor
        String[] tokens = processName.split("-");
        this.processId = Integer.parseInt(tokens[tokens.length - 1]);
    }

    // This 
    public static Props createActor(List<ActorRef> peers) {
        return Props.create(Process.class, () -> new Process(peers));
    }

    @Override
    public void postStop() {
        log.info("Process {} shutting down.", processName);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(InitPeersMessage.class, this::onInitPeers)
            .match(CrashMessage.class, msg -> {
                isCrashed = true;
                log.info("Process {} crashed!", processName);
            })
            .match(LaunchMessage.class, msg -> {
                if (!isCrashed) {
                    log.info("Process {} launching. Will do {} puts and {} gets...", processName, M, M);
                    putCount = 0;
                    getCount = 0;
                    doNextPut(); // start the first put
                }
            })
            .match(ReadPhaseRequest.class, this::onReadPhaseRequest)
            .match(ReadPhaseResponse.class, this::onReadPhaseResponse)
            .match(WritePhaseRequest.class, this::onWritePhaseRequest)
            .match(WritePhaseAck.class, this::onWritePhaseAck)

            // Optionally handle old style PutMessage/GetMessage if needed:
            .match(GetMessage.class, msg -> {
                if (!isCrashed) {
                    log.info("Ignoring direct GetMessage at process {}, we use ABD phases", processName);
                }
            })
            .match(PutMessage.class, msg -> {
                if (!isCrashed) {
                    log.info("Ignoring direct PutMessage at process {}, we use ABD phases", processName);
                }
            })
            .build();
    }

    private void onInitPeers(InitPeersMessage msg) {
        this.peers = msg.peers;
        this.N = peers.size();
        this.quorumSize = (N / 2) + 1;
        log.info("Process {} initialized with N={} peers, quorum={}", processName, N, quorumSize);
    }

    ///////////////////////////////////////////////////////////////////////////
    //                ABD logic: read-phase and write-phase handlers         //
    ///////////////////////////////////////////////////////////////////////////

    private void onReadPhaseRequest(ReadPhaseRequest req) {
        if (isCrashed) return;
        // Send back our local state
        log.info("{} Invoce read request on {}", getSender().path().name(), getSelf().path().name());
        getSender().tell(new ReadPhaseResponse(req.seqNum, localTS, localValue), getSelf());
    }

    private void onReadPhaseResponse(ReadPhaseResponse resp) {
        if (isCrashed) return;
        if (currentOp == null) return;
        if (resp.seqNum != currentOp.seqNum) return;

        currentOp.responseCount++;
        if (resp.ts > currentOp.maxTS) {
            currentOp.maxTS = resp.ts;
            currentOp.maxValue = resp.value;
        } else if (resp.ts == currentOp.maxTS && resp.value > currentOp.maxValue) {
            currentOp.maxValue = resp.value;
        }

        if (currentOp.responseCount >= currentOp.needed) {
            // time to do write phase
            int newTS;
            int newValue;
            if (currentOp.type == Operation.Type.PUT) {
                newTS = currentOp.maxTS + 1;
                newValue = currentOp.requestedValue;
            } else {
                newTS = currentOp.maxTS;
                newValue = currentOp.maxValue;
            }

            currentOp.maxTS = newTS;
            currentOp.maxValue = newValue;
            currentOp.responseCount = 0;

            WritePhaseRequest wreq = new WritePhaseRequest(currentOp.seqNum, newTS, newValue);
            for (ActorRef p : peers) {
                p.tell(wreq, getSelf());
            }
        }
    }

    private void onWritePhaseRequest(WritePhaseRequest req) {
        if (isCrashed) return;
        // adopt the new ts,value if it is bigger
        if (req.ts > localTS) {
            localTS = req.ts;
            localValue = req.value;
        } else if (req.ts == localTS && req.value > localValue) {
            localValue = req.value;
        }
        // ack
        getSender().tell(new WritePhaseAck(req.seqNum, req.ts, req.value), getSelf());
    }

    private void onWritePhaseAck(WritePhaseAck ack) {
        if (isCrashed) return;
        if (currentOp == null) return;
        if (ack.seqNum != currentOp.seqNum) return;

        currentOp.responseCount++;
        if (currentOp.responseCount >= currentOp.needed) {
            // operation done
            long elapsed = System.currentTimeMillis() - currentOp.startTime;
            if (currentOp.type == Operation.Type.PUT) {
                log.info("{} completed PUT(value={}) in {} ms", processName, currentOp.maxValue, elapsed);
                doNextPut();
            } else {
                int val = currentOp.maxValue;
                log.info("Process {} completed GET => value={} in {} ms", processName, val, elapsed);
                doNextGetDone(val);
            }
            currentOp = null;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //                   Operation Initiators (PUT/GET)                      //
    ///////////////////////////////////////////////////////////////////////////

    private void doNextPut() {
        if (putCount < M) {
            int valueToPut = putCount * peers.size() + processId;
            putCount++;
            startPut(valueToPut);
        } else {
            // done with puts => do gets
            doNextGet();
        }
    }

    private void doNextGet() {
        if (getCount < M) {
            getCount++;
            startGet();
        } else {
            log.info("{} finished all operations ({} puts, {} gets).", processName, M, M);
        }
    }

    private void doNextGetDone(int readVal) {
        // we might do something with readVal
        doNextGet();
    }

    private void startPut(int value) {
        seqCounter++;
        Operation op = new Operation(Operation.Type.PUT, seqCounter, quorumSize);
        op.requestedValue = value;
        op.startTime = System.currentTimeMillis();
        op.maxTS = 0;
        op.maxValue = 0;
        op.responseCount = 0;
        currentOp = op;

        log.info("{} starts PUT(value={}) [seq={}]", processName, value, seqCounter);
        // read phase
        ReadPhaseRequest rreq = new ReadPhaseRequest(seqCounter);

        // Ask to see all actors value
        for (ActorRef p : peers) {
            p.tell(rreq, getSelf());
        }
    }

    private void startGet() {
        seqCounter++;
        Operation op = new Operation(Operation.Type.GET, seqCounter, quorumSize);
        op.startTime = System.currentTimeMillis();
        op.maxTS = 0;
        op.maxValue = 0;
        op.responseCount = 0;
        currentOp = op;

        log.info("{} starts GET [seq={}]", processName, seqCounter);
        // read phase
        ReadPhaseRequest rreq = new ReadPhaseRequest(seqCounter);
        for (ActorRef p : peers) {
            p.tell(rreq, getSelf());
        }
    }
}
