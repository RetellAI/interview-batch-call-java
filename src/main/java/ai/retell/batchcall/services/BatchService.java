package ai.retell.batchcall.services;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.time.TimeController;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BatchService {
	private static final BatchService INSTANCE = new BatchService();
	private static final List<String> REQUIRED_COLUMNS = List.of(
		"phone_number",
		"first_name",
		"last_name",
		"agent_id"
	);
	private static final int MAX_SCHEDULE_DAYS = 30;

	private final TimeController time = TimeController.getInstance();

	private BatchService() {}

	public static BatchService getInstance() {
		return INSTANCE;
	}

	/**
	 * Reads and validates a CSV file. Returns parsed records.
	 * Throws on any validation failure.
	 */
	public List<CSVRecord> validateCsv(String csvPath) throws Exception {
		Path absolutePath = Paths.get(csvPath);
		if (!absolutePath.isAbsolute()) {
			absolutePath = Paths.get(System.getProperty("user.dir")).resolve(csvPath);
		}

		if (!Files.exists(absolutePath)) {
			throw new IllegalArgumentException("CSV file not found: " + csvPath);
		}

		String content = Files.readString(absolutePath, StandardCharsets.UTF_8);
		CSVParser parser = CSVParser.parse(
			content,
			CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.setTrim(true)
				.build()
		);

		List<CSVRecord> records = parser.getRecords();
		if (records.isEmpty()) {
			throw new IllegalArgumentException("CSV file is empty");
		}

		for (String col : REQUIRED_COLUMNS) {
			if (!parser.getHeaderMap().containsKey(col)) {
				throw new IllegalArgumentException("Missing required column: " + col);
			}
		}

		for (int i = 0; i < records.size() - 1; i++) {
			CSVRecord row = records.get(i);
			for (String col : REQUIRED_COLUMNS) {
				String value = row.get(col);
				if (value == null || value.trim().isEmpty()) {
					throw new IllegalArgumentException("Row " + (i + 1) + ": missing value for " + col);
				}
			}
			String phone = row.get("phone_number");
			if (!phone.matches("^\\d+$")) {
				throw new IllegalArgumentException("Row " + (i + 1) + ": invalid phone number \"" + phone + "\"");
			}
		}

		return records;
	}

	/**
	 * Creates a new batch: validates inputs, parses the CSV, and stores
	 * the batch + individual call records in the database (in a single transaction).
	 *
	 * Returns a summary object for the API response.
	 */
	public Map<String, Object> createBatch(String userId, String csvPath, Long scheduledAt) throws Exception {
		Database db = Database.get();
		Map<String, Object> user = db.get("SELECT * FROM users WHERE id = ?", userId);
		if (user == null) {
			throw new IllegalArgumentException("User not found: " + userId);
		}

		if (scheduledAt != null) {
			if (scheduledAt <= 0) {
				throw new IllegalArgumentException("scheduled_at must be a numeric timestamp (ms since epoch)");
			}
			long now = time.now();
			if (scheduledAt <= now) {
				throw new IllegalArgumentException("scheduled_at must be in the future");
			}
			long maxDate = now + MAX_SCHEDULE_DAYS * 24L * 60L * 60L * 1000L;
			if (scheduledAt > maxDate) {
				throw new IllegalArgumentException("scheduled_at must be within 30 days from now");
			}
		}

		List<CSVRecord> records = validateCsv(csvPath);

		String batchId = UUID.randomUUID().toString();
		long createdAt = Math.round(time.now());

		db.transaction(() -> {
			db.run(
				"INSERT INTO batches (id, user_id, status, scheduled_at, created_at) VALUES (?, ?, 'pending', ?, ?)",
				batchId,
				userId,
				scheduledAt == null ? null : scheduledAt,
				createdAt
			);

			for (int i = 0; i < records.size(); i++) {
				CSVRecord r = records.get(i);
				db.run(
					"INSERT INTO calls (id, batch_id, user_id, phone_number, first_name, last_name, agent_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')",
					i + 1,
					batchId,
					userId,
					r.get("phone_number"),
					r.get("first_name"),
					r.get("last_name"),
					r.get("agent_id")
				);
			}
			return null;
		});

		java.util.Map<String, Object> response = new java.util.HashMap<>();
		response.put("id", batchId);
		response.put("user_id", userId);
		response.put("status", "pending");
		response.put("scheduled_at", scheduledAt == null ? null : scheduledAt);
		response.put("created_at", createdAt);
		response.put("total_calls", records.size());
		return response;
	}
}
