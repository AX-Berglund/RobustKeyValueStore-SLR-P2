package demo;

// The ABD-style read and write messages used between processes
public class ABDMessages {
    
    // Sent by a process initiating a read phase to all peers
    public static class ReadPhaseRequest {
        public final int seqNum; // unique sequence number for the operation
        public ReadPhaseRequest(int seqNum) {
            this.seqNum = seqNum;
        }
    }
    
    // The response to a ReadPhaseRequest
    public static class ReadPhaseResponse {
        public final int seqNum;
        public final int ts;    // local timestamp
        public final int value; // local value
        
        public ReadPhaseResponse(int seqNum, int ts, int value) {
            this.seqNum = seqNum;
            this.ts = ts;
            this.value = value;
        }
    }
    
    // Sent by a process after determining the max timestamp from read-phase,
    // to write the (ts, value) to all peers
    public static class WritePhaseRequest {
        public final int seqNum; 
        public final int ts;    
        public final int value; 
        
        public WritePhaseRequest(int seqNum, int ts, int value) {
            this.seqNum = seqNum;
            this.ts = ts;
            this.value = value;
        }
    }
    
    // Acknowledgement from a peer after writing
    public static class WritePhaseAck {
        public final int seqNum;
        public final int ts;
        public final int value;
        
        public WritePhaseAck(int seqNum, int ts, int value) {
            this.seqNum = seqNum;
            this.ts = ts;
            this.value = value;
        }
    }
}
