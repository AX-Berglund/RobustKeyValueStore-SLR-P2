package demo; 

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import java.util.*;


public class RobustKeyValue {
    
    public static void main(String[] args) {
        final ActorSystem system = ActorSystem.create("KeyValueStoreSystem");

        int N = 5; // Number of processes
        List<ActorRef> processes = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            processes.add(system.actorOf(Process.createActor(processes), "process-" + i));
        }

        // Simulate crash and launch
        Random random = new Random();
        for (int i = 0; i < N / 2; i++) {
            int crashIdx = random.nextInt(N);
            processes.get(crashIdx).tell(new CrashMessage(), ActorRef.noSender());
        }

        for (ActorRef process : processes) {
            process.tell(new LaunchMessage(), ActorRef.noSender());
        }

        // Wait before termination
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }
}