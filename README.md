# Robust Key-Value Store

## Authors
**Axel Berglund**
**Phuc Lai Le**

## Overview
This project implements a **fault-tolerant distributed key-value store** using the Akka framework. The system allows multiple processes to store and retrieve values associated with keys, ensuring data consistency and availability even when some processes fail.

## Key Features
- **Distributed Architecture**: Operates across `N` asynchronous processes, each maintaining a local copy of the store.
- **Fault Tolerance**: Tolerates up to `f < N/2` crash failures while ensuring system correctness.
- **Consistency**: Guarantees linearizability by using timestamps for updates.
- **Concurrency**: Supports concurrent `put` and `get` operations.
- **Asynchronous Communication**: Uses reliable point-to-point channels.

## How It Works
1. **Operations**:
   - `put(key, value)`: Updates the value for a given key.
   - `get(key)`: Retrieves the most recent value for a given key.
2. **Quorum-Based Updates**:
   - To write a value, the process ensures a majority (`N - f`) store the updated value.
   - To read a value, the process queries a majority and returns the most recent value.
3. **Crash Simulation**:
   - Processes randomly crash during testing, simulating real-world failures.

## Project Structure
- **`Process`**: Actor class implementing the key-value store logic.
- **Messages**:
  - `PutMessage`: Contains key, value, and timestamp for updates.
  - `GetMessage`: Requests a value for a key.
  - `CrashMessage`: Simulates a process crash.
  - `LaunchMessage`: Initiates process operations.
- **ValueResponse**: Encapsulates the response to `get` requests.

## Running the System
1. **Setup**:
   - Ensure you have Java 8+ and the Akka framework installed.
2. **Compile and Run**:
   - Compile the project using your preferred build tool (e.g., Maven).
   - Run the `main` method in the `RobustKeyValue` class to start the system.
3. **Simulation**:
   - The system initializes `N` processes, randomly crashes `f` of them, and performs a series of `put` and `get` operations.

## Testing and Metrics
- Test configurations include different values for `N`, `f`, and operation counts.
- **Metrics**:
  - Latency: Time taken for operations to complete.
  - Consistency: Verified by checking linearizability for operations.

## Future Enhancements
- Extend to support multiple keys.
- Implement stronger failure recovery mechanisms.
- Optimize performance for large-scale deployments.

## References
- Akka Documentation: [https://akka.io/docs/](https://akka.io/docs/)
- Distributed Systems Concepts: ABD Algorithm.

