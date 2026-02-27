package ai.retell.batchcall.services;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.time.TimeController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;

/**
 * BatchProcessor – the execution engine.
 *
 * Responsibilities:
 *  1. Start a batch (set status → running, begin executing calls).
 *  2. Respect the per-user concurrency limit (never exceed 80 %).
 *  3. Schedule pending batches when their scheduled_at time arrives.
 */
public class BatchProcessor {
	private static final BatchProcessor INSTANCE = new BatchProcessor();

	private final TimeController time = TimeController.getInstance();
	private final CallSimulator callSimulator = CallSimulator.getInstance();
	private final ExecutorService executor = Executors.newCachedThreadPool();

	private ScheduledFuture<?> schedulerHandle = null;
	private boolean schedulerRunning = false;

	private BatchProcessor() {}

	public static BatchProcessor getInstance() {
		return INSTANCE;
	}

	/**
	 * Begins executing a batch. Marks it as 'running' and fills available
	 * concurrency slots with pending calls.
	 */
	public void startBatch(String batchId) throws Exception {
		Database db = Database.get();
		Map<String, Object> batch = db.get("SELECT * FROM batches WHERE id = ?", batchId);
		if (batch == null) {
			throw new IllegalArgumentException("Batch " + batchId + " not found");
		}
		if (!"pending".equals(batch.get("status"))) {
			System.out.println("[BatchProcessor] Batch " + batchId + " is already " + batch.get("status") + ", skipping");
			return;
		}

		db.run("UPDATE batches SET status = ? WHERE id = ?", "running", batchId);
		System.out.println("[BatchProcessor] Started batch " + batchId + " for " + batch.get("user_id"));
		fillSlots((String) batch.get("user_id"), batchId);
	}

	/**
	 * Starts the scheduler loop that checks once per (simulated) minute for
	 * pending batches whose scheduled_at has arrived.
	 */
	public void startScheduler() {
		schedulerRunning = true;
		final Runnable[] checkHolder = new Runnable[1];
		checkHolder[0] = () -> {
			try {
				Database db = Database.get();
				long now = Math.round(time.now());
				List<Map<String, Object>> dueBatches = db.all(
					"SELECT * FROM batches WHERE status = 'pending' AND scheduled_at IS NOT NULL AND scheduled_at <= ?",
					now
				);

				for (Map<String, Object> batch : dueBatches) {
					System.out.println("[Scheduler] Batch " + batch.get("id") + " is due, starting…");
					startBatch((String) batch.get("id"));
				}
			} catch (Exception err) {
				System.err.println("[Scheduler] Error: " + err.getMessage());
			}

			if (schedulerRunning) {
				schedulerHandle = time.setTimeout(checkHolder[0], 60000);
			}
		};
		schedulerHandle = time.setTimeout(checkHolder[0], 60000);
		System.out.println("[Scheduler] Started");
	}

	public void stopScheduler() {
		schedulerRunning = false;
		if (schedulerHandle != null) {
			time.clearTimeout(schedulerHandle);
			schedulerHandle = null;
		}
		System.out.println("[Scheduler] Stopped");
	}

	private void fillSlots(String userId, String batchId) throws Exception {
		Database db = Database.get();
		Map<String, Object> user = db.get("SELECT * FROM users WHERE id = ?", userId);
		int maxConcurrent = ((Number) user.get("concurrency_limit")).intValue();

		Map<String, Object> activeRow = db.get(
			"SELECT COUNT(*) as count FROM calls WHERE user_id = ? AND status = 'in_progress'",
			userId
		);
		int activeCount = ((Number) activeRow.get("count")).intValue();

		int available = maxConcurrent - activeCount;
		if (available <= 0) {
			return;
		}

		List<Map<String, Object>> pendingCalls = db.all(
			"SELECT * FROM calls WHERE batch_id = ? AND status = 'pending' LIMIT ?",
			batchId,
			available
		);

		for (Map<String, Object> call : pendingCalls) {
			CompletableFuture.runAsync(() -> {
				try {
					executeCall(call, userId, batchId);
				} catch (Exception err) {
					System.err.println("[BatchProcessor] Unhandled error in executeCall for call " + call.get("id") + ": " + err.getMessage());
				}
			}, executor);
		}
	}

	private void executeCall(Map<String, Object> call, String userId, String batchId) throws Exception {
		Database db = Database.get();
		long now = Math.round(time.now());
		db.run(
			"UPDATE calls SET status = ?, started_at = ? WHERE id = ?",
			"in_progress",
			now,
			call.get("id")
		);

		System.out.println("[BatchProcessor] Calling (user " + userId + ", batch " + batchId + "), call " + call.get("id") + ", number " + call.get("phone_number"));

		try {
			long durationMs = callSimulator.simulateCall().get();
			long completedAt = Math.round(time.now());

			db.run(
				"UPDATE calls SET status = ?, completed_at = ?, duration_ms = ? WHERE id = ?",
				"completed",
				completedAt,
				durationMs,
				call.get("id")
			);

			System.out.println("[BatchProcessor] Completed call for user " + userId + ", batch " + batchId + ", call " + call.get("id") + ", number " + call.get("phone_number") + " (" + durationMs + "ms sim)");

			Map<String, Object> remainingRow = db.get(
				"SELECT COUNT(*) as count FROM calls WHERE batch_id = ? AND status IN ('pending', 'in_progress')",
				batchId
			);
			int remaining = ((Number) remainingRow.get("count")).intValue();

			if (remaining == 0) {
				long batchDoneAt = Math.round(time.now());
				db.run(
					"UPDATE batches SET status = ?, completed_at = ? WHERE id = ?",
					"completed",
					batchDoneAt,
					batchId
				);
				System.out.println("[BatchProcessor] Batch " + batchId + " completed for user " + userId);
			} else {
				fillSlots(userId, batchId);
			}
		} catch (Exception err) {
			db.run("UPDATE calls SET status = 'failed' WHERE id = ?", call.get("id"));
			System.err.println("[BatchProcessor] Call " + call.get("id") + " failed for user " + userId + ", batch " + batchId + ", number " + call.get("phone_number") + ": " + err.getMessage());
			try {
				fillSlots(userId, batchId);
			} catch (Exception fillErr) {
				System.err.println("[BatchProcessor] Error filling slots after failure: " + fillErr.getMessage());
			}
		}
	}
}
