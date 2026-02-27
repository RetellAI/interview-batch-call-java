## Bugs

- If a batch starts when a user is already at the concurrency limit, `fillSlots` returns and never retries; the batch can remain `running` forever because only the current batch triggers refills (src/main/java/ai/retell/batchcall/services/BatchProcessor.java).
- Concurrency enforcement is non-atomic: separate "count active" and "start call" steps allow multiple batches to exceed a user's limit under contention (src/main/java/ai/retell/batchcall/services/BatchProcessor.java).
- Batch completion is only checked on the success path; if the last call fails (or all calls fail), the batch never transitions to `completed` (src/main/java/ai/retell/batchcall/services/BatchProcessor.java).
- Scheduler loop has no error isolation; any thrown error (DB call, etc.) breaks future scheduling (src/main/java/ai/retell/batchcall/services/BatchProcessor.java).
- CSV validation skips the last record (`for ... size() - 1`), so the final row is never validated before insertion (src/main/java/ai/retell/batchcall/services/BatchService.java).
- Call IDs are manually set starting at 1 for every batch, which violates the AUTOINCREMENT primary key and causes a UNIQUE constraint error on the second batch (src/main/java/ai/retell/batchcall/services/BatchService.java).
- Pending batches with `scheduled_at = NULL` are only started on creation; after a server restart they remain `pending` forever because the scheduler only checks scheduled batches (src/main/java/ai/retell/batchcall/services/BatchProcessor.java).

## Improvements

- Track active calls in memory (or with a lightweight counter) to avoid frequent DB scans for concurrency checks.
- Rate-limit or stagger call starts to avoid traffic spikes when a batch begins.
- Avoid full table scans for batch status summaries; maintain counters or incremental aggregates.
- Add indexes for `batches.scheduled_at` and `calls.batch_id` to improve scheduler and batch lookups.
