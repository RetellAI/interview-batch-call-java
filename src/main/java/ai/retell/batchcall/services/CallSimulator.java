package ai.retell.batchcall.services;

import ai.retell.batchcall.time.TimeController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class CallSimulator {
	public static class CallSimulationError extends RuntimeException {
		public CallSimulationError(String message) {
			super(message);
		}
	}

	private static final CallSimulator INSTANCE = new CallSimulator();

	private double callFailureRate = 0.0;
	private boolean forceNextCallFailure = false;
	private final TimeController time = TimeController.getInstance();

	private CallSimulator() {}

	public static CallSimulator getInstance() {
		return INSTANCE;
	}

	public void forceNextCallFailure() {
		forceNextCallFailure = true;
	}

	public void resetCallFailureState() {
		callFailureRate = 0.0;
		forceNextCallFailure = false;
	}

	public double getCallFailureRate() {
		return callFailureRate;
	}

	public void setCallFailureRate(double rate) {
		callFailureRate = rate;
	}

	/**
	 * Simulates a single phone call.
	 * The call lasts a random duration between 10 seconds and 2 minutes.
	 * Returns a promise that resolves with the simulated duration (in ms).
	 */
	public CompletableFuture<Long> simulateCall() {
		if (forceNextCallFailure) {
			forceNextCallFailure = false;
			CompletableFuture<Long> failed = new CompletableFuture<>();
			failed.completeExceptionally(new CallSimulationError("Simulated call failure"));
			return failed;
		}
		if (callFailureRate > 0 && Math.random() < callFailureRate) {
			CompletableFuture<Long> failed = new CompletableFuture<>();
			failed.completeExceptionally(new CallSimulationError("Simulated call failure"));
			return failed;
		}

		CompletableFuture<Long> promise = new CompletableFuture<>();
		long durationMs = ThreadLocalRandom.current().nextLong(10000, 120000 + 1);
		time.setTimeout(() -> promise.complete(durationMs), durationMs);
		return promise;
	}
}
