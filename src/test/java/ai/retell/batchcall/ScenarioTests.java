package ai.retell.batchcall;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.services.BatchProcessor;
import ai.retell.batchcall.services.BatchService;
import ai.retell.batchcall.services.CallSimulator;
import ai.retell.batchcall.time.TimeController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ScenarioTests {
	private static Path tmpDir;

	private final BatchService batchService = BatchService.getInstance();
	private final BatchProcessor batchProcessor = BatchProcessor.getInstance();
	private final TimeController time = TimeController.getInstance();

	@BeforeAll
	static void beforeAll() throws Exception {
		tmpDir = Files.createTempDirectory("batch-scenarios-");
		System.setProperty("BATCH_CALLS_DB_PATH", tmpDir.resolve("batch_calls.db").toString());
		Database.init();
	}

	@BeforeEach
	void beforeEach() throws Exception {
		Database db = Database.get();
		db.run("DELETE FROM calls");
		db.run("DELETE FROM batches");
		time.reset();
		time.setSpeed(300);
		batchProcessor.stopScheduler();
		db.resetFailureState();
		CallSimulator.getInstance().resetCallFailureState();
	}

	@AfterEach
	void afterEach() {
		batchProcessor.stopScheduler();
	}

	@Test
	void caseA() throws Exception {
		Path csvPath = tmpDir.resolve("valid.csv");
		makeValidCsv(csvPath);

		Map<String, Object> first = batchService.createBatch("user_1", csvPath.toString(), null);
		assertNotNull(first.get("id"));

		Map<String, Object> second = batchService.createBatch("user_1", csvPath.toString(), null);
		assertNotNull(second.get("id"));
	}

	@Test
	void caseB() throws Exception {
		Path csvPath = tmpDir.resolve("bad_last_row.csv");
		makeBadLastRowCsv(csvPath);

		assertThrows(Exception.class, () -> batchService.validateCsv(csvPath.toString()));
	}

	@Test
	void caseC() throws Exception {
		Path csvPath = tmpDir.resolve("valid.csv");
		makeValidCsv(csvPath);
		Map<String, Object> batch = batchService.createBatch("user_1", csvPath.toString(), null);
		assertEquals("pending", batch.get("status"));

		batchProcessor.startScheduler();
		Database db = Database.get();

		awaitSimulated(65000, () -> {
			try {
				Map<String, Object> refreshed = db.get("SELECT status FROM batches WHERE id = ?", batch.get("id"));
				String status = (String) refreshed.get("status");
				assertTrue("running".equals(status) || "completed".equals(status));
			} catch (Exception err) {
				throw new RuntimeException(err);
			}
		});
	}

	@Test
	void caseD() throws Exception {
		time.setSpeed(50);

		Path csvPath = tmpDir.resolve("case_d.csv");
		writeCsv(csvPath, List.of(
			"phone_number,first_name,last_name,agent_id",
			"5550001000,A,One,agent_1",
			"5550002000,B,Two,agent_1",
			"5550003000,C,Three,agent_1",
			"5550004000,D,Four,agent_1",
			"5550005000,E,Five,agent_1"
		));

		Path csvPath2 = tmpDir.resolve("case_d_2.csv");
		writeCsv(csvPath2, List.of(
			"phone_number,first_name,last_name,agent_id",
			"5551001000,F,Six,agent_1",
			"5551002000,G,Seven,agent_1"
		));

		Map<String, Object> batch1 = batchService.createBatch("user_3", csvPath.toString(), null);
		Map<String, Object> batch2 = batchService.createBatch("user_3", csvPath2.toString(), null);

		batchProcessor.startScheduler();
		Database db = Database.get();

		awaitSimulated(200000, () -> {
			try {
				Map<String, Object> refreshed = db.get("SELECT status FROM batches WHERE id = ?", batch1.get("id"));
				String status = (String) refreshed.get("status");
				assertTrue("running".equals(status) || "completed".equals(status));
			} catch (Exception err) {
				throw new RuntimeException(err);
			}
		});

		awaitSimulated(200000, () -> {
			try {
				Map<String, Object> refreshed = db.get("SELECT status FROM batches WHERE id = ?", batch2.get("id"));
				String status = (String) refreshed.get("status");
				assertEquals("completed", status, "batch should be completed, got " + status);
			} catch (Exception err) {
				throw new RuntimeException(err);
			}
		});
	}

	private void writeCsv(Path filePath, List<String> rows) throws Exception {
		Files.writeString(filePath, String.join("\n", rows), StandardCharsets.UTF_8);
	}

	private void makeValidCsv(Path filePath) throws Exception {
		writeCsv(filePath, List.of(
			"phone_number,first_name,last_name,agent_id",
			"1234567890,Jane,Doe,agent_1",
			"2345678901,John,Smith,agent_2"
		));
	}

	private void makeBadLastRowCsv(Path filePath) throws Exception {
		writeCsv(filePath, List.of(
			"phone_number,first_name,last_name,agent_id",
			"1234567890,Jane,Doe,agent_1",
			"NOT_A_NUMBER,John,Smith,agent_2"
		));
	}

	private void awaitSimulated(long simDelayMs, Runnable assertion) throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<Throwable> error = new AtomicReference<>();

		time.setTimeout(() -> {
			try {
				assertion.run();
			} catch (Throwable t) {
				error.set(t);
			} finally {
				latch.countDown();
			}
		}, simDelayMs);

		if (!latch.await(10, TimeUnit.SECONDS)) {
			fail("Timed out waiting for simulated delay");
		}
		if (error.get() != null) {
			Throwable t = error.get();
			if (t instanceof AssertionError) {
				throw (AssertionError) t;
			}
			throw new AssertionError(t);
		}
	}
}
