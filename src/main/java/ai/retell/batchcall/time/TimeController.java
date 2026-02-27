package ai.retell.batchcall.time;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TimeController - manages simulated time for the batch call system.
 *
 * In normal mode (speedFactor = 1) it behaves like real time.
 * By default, we run in simulation mode (speedFactor = 300) so you can
 * observe batch behavior quickly.
 * In simulation mode, time advances faster by the given speed factor.
 *
 * NOTE: This module is trusted and has no bugs
 */
public class TimeController {
	private static final TimeController INSTANCE = new TimeController();
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	private int speedFactor = 300;
	private long baseRealTime = System.currentTimeMillis();
	private long baseSimTime = System.currentTimeMillis();

	private TimeController() {}

	public static TimeController getInstance() {
		return INSTANCE;
	}

	/** Returns the current simulated timestamp (ms since epoch). */
	public long now() {
		long realElapsed = System.currentTimeMillis() - baseRealTime;
		return baseSimTime + realElapsed * speedFactor;
	}

	/**
	 * Schedules a callback after `simDelayMs` milliseconds of simulated time.
	 * In simulation mode, the real delay is shorter by the speed factor.
	 */
	public ScheduledFuture<?> setTimeout(Runnable callback, long simDelayMs) {
		double realDelay = simDelayMs / (double) speedFactor;
		long delayMs = Math.max(1, Math.round(realDelay));
		return scheduler.schedule(callback, delayMs, TimeUnit.MILLISECONDS);
	}

	public void clearTimeout(ScheduledFuture<?> handle) {
		if (handle != null) {
			handle.cancel(false);
		}
	}

	/**
	 * Sets the simulation speed factor and recalibrates the time base
	 * so there is no jump in the simulated clock.
	 */
	public void setSpeed(int factor) {
		this.baseSimTime = now();
		this.baseRealTime = System.currentTimeMillis();
		this.speedFactor = factor;
	}

	/** Re-anchors simulated time to now, preventing accumulation across test runs. */
	public void reset() {
		this.baseRealTime = System.currentTimeMillis();
		this.baseSimTime = System.currentTimeMillis();
	}
}
