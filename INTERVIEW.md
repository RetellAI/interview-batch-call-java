# Batch Call System

## Overview

- This system lets a user upload a CSV of phone numbers to call in a batch. Sample CSV files live in `data/` for testing.
- The actual calling logic is mocked with timers, using a random duration between 10 seconds and 2 minutes.
- A batch can be scheduled up to one month in the future, or started immediately.
- Each user has a concurrency limit (maximum simultaneous calls).
- The system should avoid spiky traffic.
- The repo includes a time simulation to speed up time. You can trust that this simulation has no issues. Use it to observe system behavior.

## Sample commands

# Start the server:

```sh
./gradlew bootRun
```

# Submit a new batch (scheduled 10 minutes from now):

```sh
curl -X POST http://localhost:3456/api/batches \
  -H "Content-Type: application/json" \
  -d "{\"user_id\": \"user_1\", \"csv_path\": \"data/small_batch.csv\", \"scheduled_at\": $(($(date +%s) * 1000 + 600000))}"
```

Time acceleration is enabled by default (simulated time runs 300x faster) so you can observe batch behavior quickly.

## Scenario tests

The repo includes a small scenario test runner that describes expected behavior in realistic cases. Use it to observe, debug, and verify fixes.

```sh
./scripts/scenario-tests.sh
```

To run a single case:

```sh
./scripts/scenario-tests.sh case-a
```

## Tasks

Follow the tasks in order and share your thought process. Tasks 3 and 4 are optional and time permitting.

## Expected approach

- Focus on reasoning and trade-offs; you do not need to fully implement every fix or feature.
- For debugging, identify root causes and suggest targeted fixes; minimal code changes are fine.
- For improvements, prioritize scalability, fairness, and reliability; explain what you would measure or log.

## Known limitations

- The time simulation in `src/main/java/ai/retell/batchcall/time/TimeController.java` is trusted and does not need debugging.
- This is an interview exercise; aim for clarity over completeness.

### Task 1: Understand

Navigate through the codebase and understand what each part is doing.

### Task 2: Debug

There might be bugs in this repo. Find and fix them.

### Task 3: Improvement (optional)

Identify improvements and draft an approach. Consider performance blockers and scale risks as data volume grows.

### Task 4: New feature (optional)

- Add a daily time window for each batch that controls when calls are allowed to run. If a batch does not finish within its window, it should pause and resume in the next window.
- The overall system has a global concurrency limit, and fairness is required so a single user's large batch cannot take over all resources. Implement the fairness logic.
