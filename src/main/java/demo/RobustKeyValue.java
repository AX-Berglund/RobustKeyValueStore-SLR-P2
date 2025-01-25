package demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class RobustKeyValue {

    public static void main(String[] args) {
        
        final ActorSystem system = ActorSystem.create("System");

        int N = 5;
        int f = 1; // up to 1 crash => f < N/2 is satisfied for N=5

        
        // We'll do a separate list for the final references
        List<ActorRef> actors = new ArrayList<>();

        // We'll first create them with an empty list
        for (int i = 0; i < N; i++) {
            actors.add(system.actorOf(Process.createActor(new ArrayList<>()), "process-" + i));
        }
        // Now we have all actors. We'll pass that same list to each constructor via a special "InitPeersMessage" 
        // So we do an init message:
        for (ActorRef ref : actors) {
            ref.tell(new InitPeersMessage(actors), ActorRef.noSender());
        }

        // Now we can proceed with the random crashing. Let's shuffle the list and pick the first f processes to crash:
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            indices.add(i);
        }
        Collections.shuffle(indices, new Random());
        for (int i = 0; i < f; i++) {
            int crashIdx = indices.get(i);
            actors.get(crashIdx).tell(new CrashMessage(), ActorRef.noSender());
        }

        // The rest are correct (greater than f) => send them a LaunchMessage
        for (int i = f; i < N; i++) {
            int idx = indices.get(i);
            actors.get(idx).tell(new LaunchMessage(), ActorRef.noSender());
            
            try {
                TimeUnit.SECONDS.sleep(15); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); 
            }
        }

        // Let them run for some time
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }
}

// A helper message so each Process can be re-initialized with the final peer list
class InitPeersMessage {
    public final List<ActorRef> peers;
    public InitPeersMessage(List<ActorRef> peers) {
        this.peers = peers;
    }
}
