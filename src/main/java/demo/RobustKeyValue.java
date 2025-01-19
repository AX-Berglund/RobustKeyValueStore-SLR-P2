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

        // Perform operations. the arguments of PutMessage are key, value, timestamp
        processes.get(0).tell(new PutMessage(1, 100, 1), ActorRef.noSender());
        processes.get(1).tell(new GetMessage(1), ActorRef.noSender());

        // Crash and relaunch simulation
        Random random = new Random();
        for (int i = 0; i < N / 2; i++) {
            int crashIdx = random.nextInt(N);
            processes.get(crashIdx).tell(new CrashMessage(), ActorRef.noSender());
        }

        for (ActorRef process : processes) {
            process.tell(new LaunchMessage(), ActorRef.noSender());
        }

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            system.terminate();
        }
    }
}
