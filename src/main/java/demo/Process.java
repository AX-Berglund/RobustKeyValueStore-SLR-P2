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
	
	//LAI To calculate the average latency of PUT and GET
	private List<Long> putLatencies = new ArrayList<>();
	private List<Long> getLatencies = new ArrayList<>();
	private ActorRef aggregator; // Reference to the aggregator process
	

	
	
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
        public boolean maxReadResponseFlag;
        public boolean maxWriteResponseFlag;
        public int maxTS;         
        public int maxValue;      
        public int responseCount; 
        public int needed;        
        public long startTime;    
        public Operation(Type type, int seqNum, int needed, boolean maxReadResponseFlag,boolean maxWriteResponseFlag) {
            this.type = type;
            this.seqNum = seqNum;
            this.needed = needed;
            this.maxReadResponseFlag = maxReadResponseFlag;
            this.maxWriteResponseFlag = maxWriteResponseFlag;


        }
    }

    // For demonstration, each process has an index (processName) so we know who is who
    private final int processId;
    private final String processName;

    // For the sequential M puts + M gets scenario
    private static final int M = 200;
    private int putCount = 0;
    private int getCount = 0;



    public Process(List<ActorRef> initialPeers, ActorRef aggregator) {
        // We'll set peers after we get an InitPeersMessage, 
        // but let's store what we have for now if any.
        this.peers = initialPeers;
        this.processName = getSelf().path().name(); // get the name of the actor
        String[] tokens = processName.split("-");
        this.processId = Integer.parseInt(tokens[tokens.length - 1]);
        
        //LAI
        this.aggregator = aggregator; // Store reference to aggregator
        
    }

    // This 
    public static Props createActor(List<ActorRef> peers,  ActorRef aggregator) {
        return Props.create(Process.class, () -> new Process(peers, aggregator));
    }

    @Override
    public void postStop() {
        log.info("{} shutting down.", processName);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
            .match(InitPeersMessage.class, this::onInitPeers)
            .match(CrashMessage.class, msg -> {
                isCrashed = true;
                log.info("[Process-{}] [Crash] Will not react to further messages.", processId);
            })
            .match(LaunchMessage.class, msg -> {
                if (!isCrashed) {
                    log.info("[Process-{}] [Launch] Process launching with {} PUTs and {} GETs.", processId, M, M);
                    putCount = 0;
                    getCount = 0;
                    doNextPut(); // start the first put
                }
            })
            .match(ReadPhaseRequest.class, this::onReadPhaseRequest)
            .match(ReadPhaseResponse.class, this::onReadPhaseResponse)
            .match(WritePhaseRequest.class, this::onWritePhaseRequest)
            .match(WritePhaseAck.class, this::onWritePhaseAck)
            .build();
    }

    private void onInitPeers(InitPeersMessage msg) {
        this.peers = msg.peers;
        this.N = peers.size();
        this.quorumSize = (N / 2) + 1;
        log.info("[Process-{}] [Init] Initialized with N={} peers, quorum={}", processId, N, quorumSize);
    }

    ///////////////////////////////////////////////////////////////////////////
    //                ABD logic: read-phase and write-phase handlers         //
    ///////////////////////////////////////////////////////////////////////////

    private void onReadPhaseRequest(ReadPhaseRequest req) {
        if (isCrashed) return;

        // Send back our local state
        
        getSender().tell(new ReadPhaseResponse(req.seqNum, localTS, localValue), getSelf());
    }

    private void onReadPhaseResponse(ReadPhaseResponse resp) {
        if (isCrashed) return;
        
        
        // Check if there's an ongoing operation
        if (currentOp == null) {
            log.warning("{}: Received unexpected ReadPhaseResponse (seqNum={})",
                    processName, resp.seqNum);
            return;
        }

        if (currentOp.maxReadResponseFlag){
            return;
        }

        if (currentOp.responseCount >= currentOp.needed){return;}


        log.info("[{}] [ReadPhaseRequest] Received read request from {} with seq={}.",
            getSender().path().name(), processName, resp.seqNum);

        
       // Update regardless of seqNum (both PUT and GET operations can receive responses)
        currentOp.responseCount++;

        if (resp.ts > currentOp.maxTS) {
            currentOp.maxTS = resp.ts;
            currentOp.maxValue = resp.value;
        } else if (resp.ts == currentOp.maxTS && resp.value > currentOp.maxValue) {
            currentOp.maxValue = resp.value;
        }

        if (currentOp.responseCount >= currentOp.needed) {
            // time to write
            int newTS;
            int newValue;
            if (currentOp.type == Operation.Type.PUT) {
                newTS = currentOp.maxTS + 1;
                newValue = currentOp.requestedValue;

                currentOp.maxTS = newTS;
                currentOp.maxValue = newValue;
                currentOp.responseCount = 0;
                currentOp.maxReadResponseFlag = true;

                log.info("[Process-{}] [Quorum] Sending write requests to peers", processId);

                WritePhaseRequest wreq = new WritePhaseRequest(currentOp.seqNum, newTS, newValue);
                for (ActorRef p : peers) {
                    p.tell(wreq, getSelf());
                }
            } else {
//                long elapsed = System.currentTimeMillis() - currentOp.startTime;
//                log.info("[Process-{}] [Quorum] Majority reads", processId);
//                log.info("[Process-{}] [GET-{}] Completed GET => (value={}) in {} ms.", processId, seqCounter, resp.value, elapsed);
               
            	
                log.info("[Process-{}] [Quorum] Sending write requests to peers", processId);
            	
                WritePhaseRequest wreq = new WritePhaseRequest(currentOp.seqNum, currentOp.maxTS, currentOp.maxValue);
                for (ActorRef p : peers) {
                    p.tell(wreq, getSelf());
                }
                
                currentOp.responseCount = 0;
                currentOp.maxReadResponseFlag = true;
//                doNextGet();

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
        getSender().tell(new WritePhaseAck(req.seqNum, localTS, localValue), getSelf());
    }

    private void onWritePhaseAck(WritePhaseAck ack) {
        if (isCrashed) return;
        
        if (currentOp == null) return;

        if (ack.seqNum != currentOp.seqNum) return;


        log.info("[{}] [ACK] Updated local state to value={} with timestamp={}.",
              getSender().path().name(), ack.value, ack.ts);


        currentOp.responseCount++;
        if (currentOp.responseCount >= currentOp.needed) {
            // operation done
            long elapsed = System.currentTimeMillis() - currentOp.startTime;
            if (currentOp.type == Operation.Type.PUT) {
            	putLatencies.add(elapsed);
                log.info("[Process-{}] [PUT-{}] Completed PUT(value={}) in {} ms.", processId, seqCounter, ack.value, elapsed);
                doNextPut();
//                currentOp.responseCount = 0;

            } else {
            	getLatencies.add(elapsed);
                int val = currentOp.maxValue;
                log.info("[Process-{}] [GET-{}] Completed GET => (value={}) in {} ms.", processId, seqCounter, val, elapsed);
                doNextGet();
//              currentOp.responseCount = 0;
                // doNextGetDone(val);
            }
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
            currentOp=null;
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
            
            sendLatenciesToAggregator();
        }
    }


    private void startPut(int value) {
        seqCounter++;
        Operation op = new Operation(Operation.Type.PUT, seqCounter, quorumSize, false, false);
        op.requestedValue = value;
        op.startTime = System.currentTimeMillis();
        op.maxTS = 0;
        op.maxValue = 0;
        op.responseCount = 0;
        currentOp = op;

        log.info("[Process-{}] [PUT-{}] Starting PUT(value={}) [seq={}].", processId, seqCounter, value, seqCounter);
        // read phase
        ReadPhaseRequest rreq = new ReadPhaseRequest(seqCounter);

        // Send ReadPhaseRequest to all actors
        for (ActorRef p : peers) {
            p.tell(rreq, getSelf());
        }
    }

    private void startGet() {
        // log.info("First we have {}", currentOp.type);
        seqCounter++;
        Operation op2 = new Operation(Operation.Type.GET, seqCounter, quorumSize, false, false);
        op2.startTime = System.currentTimeMillis();
        op2.maxTS = 0;
        op2.maxValue = 0;
        op2.responseCount = 0;
        currentOp = op2;


        log.info("[Process-{}] [GET-{}] Starting GET [seq={}].", processId, seqCounter, seqCounter);

        // read phase
        ReadPhaseRequest rreq = new ReadPhaseRequest(seqCounter);

        for (ActorRef p : peers) {
            p.tell(rreq, getSelf());
        }
    }
    
    private void sendLatenciesToAggregator() {
        double avgPutLatency = putLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double avgGetLatency = getLatencies.stream().mapToLong(Long::longValue).average().orElse(0.0);

        log.info("[Process-{}] Sending latencies to aggregator (PUT: {} ms, GET: {} ms).", 
            processId, avgPutLatency, avgGetLatency);

        aggregator.tell(new LatencyReportMessage(avgPutLatency, avgGetLatency), getSelf());
    }


}
