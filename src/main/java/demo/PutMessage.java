package demo;

class PutMessage {
    public final int key; 
    public final int value; 
    public final int timestamp;
    public PutMessage(int key, int value, int timestamp) {
        this.key = key; 
        this.value = value;
        this.timestamp = timestamp;
    }
}