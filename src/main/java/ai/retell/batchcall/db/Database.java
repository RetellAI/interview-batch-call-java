package ai.retell.batchcall.db;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;

public class Database {
	private static final int MIN_LATENCY_MS = 20;
	private static final int MAX_LATENCY_MS = 100;
	private static volatile Database INSTANCE;

	private final Connection connection;
	private final ReentrantLock lock = new ReentrantLock(true);

	private double failureRate = 0.0;
	private boolean forceNextFailure = false;

	private Database(Connection connection) {
		this.connection = connection;
	}

	public static synchronized void init() throws Exception {
		if (INSTANCE != null) {
			return;
		}

		String configured = System.getProperty("BATCH_CALLS_DB_PATH");
		if (configured == null || configured.isBlank()) {
			configured = System.getenv("BATCH_CALLS_DB_PATH");
		}

		Path dbPath;
		if (configured != null && !configured.isBlank()) {
			dbPath = Paths.get(configured);
		} else {
			dbPath = Paths.get(System.getProperty("user.dir")).resolve("batch_calls.db");
		}

		Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
		Database db = new Database(conn);
		db.exec("PRAGMA busy_timeout = 5000");
		db.exec("PRAGMA foreign_keys = ON");

		db.exec("""
			CREATE TABLE IF NOT EXISTS users (
			  id TEXT PRIMARY KEY,
			  concurrency_limit INTEGER NOT NULL DEFAULT 10
			)
			""");
		db.exec("""
			CREATE TABLE IF NOT EXISTS batches (
			  id TEXT PRIMARY KEY,
			  user_id TEXT NOT NULL,
			  status TEXT NOT NULL DEFAULT 'pending',
			  scheduled_at INTEGER,
			  created_at INTEGER NOT NULL,
			  completed_at INTEGER,
			  FOREIGN KEY (user_id) REFERENCES users(id)
			)
			""");
		db.exec("""
			CREATE TABLE IF NOT EXISTS calls (
			  id INTEGER PRIMARY KEY AUTOINCREMENT,
			  batch_id TEXT NOT NULL,
			  user_id TEXT NOT NULL,
			  phone_number TEXT NOT NULL,
			  first_name TEXT NOT NULL,
			  last_name TEXT NOT NULL,
			  agent_id TEXT NOT NULL,
			  status TEXT NOT NULL DEFAULT 'pending',
			  started_at INTEGER,
			  completed_at INTEGER,
			  duration_ms INTEGER,
			  FOREIGN KEY (batch_id) REFERENCES batches(id),
			  FOREIGN KEY (user_id) REFERENCES users(id)
			)
			""");
		db.exec("CREATE INDEX IF NOT EXISTS idx_calls_user_id ON calls(user_id)");
		db.exec("CREATE INDEX IF NOT EXISTS idx_calls_status ON calls(status)");
		db.exec("CREATE INDEX IF NOT EXISTS idx_batches_user_id ON batches(user_id)");
		db.exec("CREATE INDEX IF NOT EXISTS idx_batches_status ON batches(status)");

		List<Map<String, Object>> seedUsers = List.of(
			Map.of("id", "user_1", "concurrency_limit", 10),
			Map.of("id", "user_2", "concurrency_limit", 20),
			Map.of("id", "user_3", "concurrency_limit", 5)
		);
		for (Map<String, Object> u : seedUsers) {
			db.run(
				"INSERT OR IGNORE INTO users (id, concurrency_limit) VALUES (?, ?)",
				u.get("id"),
				u.get("concurrency_limit")
			);
		}

		INSTANCE = db;
	}

	public static Database get() {
		if (INSTANCE == null) {
			throw new IllegalStateException("Database not initialised – call Database.init() first");
		}
		return INSTANCE;
	}

	public double getFailureRate() {
		return failureRate;
	}

	public void setFailureRate(double rate) {
		this.failureRate = rate;
	}

	public void forceNextFailure() {
		this.forceNextFailure = true;
	}

	public void resetFailureState() {
		this.failureRate = 0.0;
		this.forceNextFailure = false;
	}

	public Map<String, Object> get(String sql, Object... params) throws Exception {
		return withFailureAndLatency(() -> {
			lock.lock();
			try (PreparedStatement stmt = connection.prepareStatement(sql)) {
				bindParams(stmt, params);
				try (ResultSet rs = stmt.executeQuery()) {
					if (!rs.next()) {
						return null;
					}
					return mapRow(rs);
				}
			} finally {
				lock.unlock();
			}
		});
	}

	public List<Map<String, Object>> all(String sql, Object... params) throws Exception {
		return withFailureAndLatency(() -> {
			lock.lock();
			try (PreparedStatement stmt = connection.prepareStatement(sql)) {
				bindParams(stmt, params);
				try (ResultSet rs = stmt.executeQuery()) {
					List<Map<String, Object>> rows = new ArrayList<>();
					while (rs.next()) {
						rows.add(mapRow(rs));
					}
					return rows;
				}
			} finally {
				lock.unlock();
			}
		});
	}

	public int run(String sql, Object... params) throws Exception {
		return withFailureAndLatency(() -> {
			lock.lock();
			try (PreparedStatement stmt = connection.prepareStatement(sql)) {
				bindParams(stmt, params);
				return stmt.executeUpdate();
			} finally {
				lock.unlock();
			}
		});
	}

	public void exec(String sql) throws Exception {
		withFailureAndLatency(() -> {
			lock.lock();
			try (Statement stmt = connection.createStatement()) {
				stmt.execute(sql);
				return null;
			} finally {
				lock.unlock();
			}
		});
	}

	public <T> T transaction(Callable<T> fn) throws Exception {
		lock.lock();
		try {
			exec("BEGIN");
			T result = fn.call();
			exec("COMMIT");
			return result;
		} catch (Exception err) {
			exec("ROLLBACK");
			throw err;
		} finally {
			lock.unlock();
		}
	}

	private <T> T withFailureAndLatency(Callable<T> action) throws Exception {
		maybeFail();
		randomDelay();
		return action.call();
	}

	private void randomDelay() throws InterruptedException {
		int ms = ThreadLocalRandom.current().nextInt(MIN_LATENCY_MS, MAX_LATENCY_MS + 1);
		Thread.sleep(ms);
	}

	private void maybeFail() {
		if (forceNextFailure) {
			forceNextFailure = false;
			throw new DatabaseError("Simulated DB failure");
		}
		if (failureRate > 0 && Math.random() < failureRate) {
			throw new DatabaseError("Simulated DB failure");
		}
	}

	private void bindParams(PreparedStatement stmt, Object... params) throws Exception {
		if (params == null) {
			return;
		}
		for (int i = 0; i < params.length; i++) {
			stmt.setObject(i + 1, params[i]);
		}
	}

	private Map<String, Object> mapRow(ResultSet rs) throws Exception {
		ResultSetMetaData meta = rs.getMetaData();
		int columns = meta.getColumnCount();
		Map<String, Object> row = new LinkedHashMap<>();
		for (int i = 1; i <= columns; i++) {
			String name = meta.getColumnLabel(i);
			row.put(name, rs.getObject(i));
		}
		return row;
	}
}
