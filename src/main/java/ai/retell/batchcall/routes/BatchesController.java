package ai.retell.batchcall.routes;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.services.BatchProcessor;
import ai.retell.batchcall.services.BatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchesController {
	private final BatchService batchService = BatchService.getInstance();
	private final BatchProcessor batchProcessor = BatchProcessor.getInstance();

	@PostMapping
	public ResponseEntity<?> createBatch(@RequestBody Map<String, Object> body) {
		try {
			String userId = body.get("user_id") instanceof String ? (String) body.get("user_id") : null;
			String csvPath = body.get("csv_path") instanceof String ? (String) body.get("csv_path") : null;
			Long scheduledAt = null;
			if (body.containsKey("scheduled_at") && body.get("scheduled_at") != null) {
				Object raw = body.get("scheduled_at");
				if (!(raw instanceof Number)) {
					return ResponseEntity.badRequest().body(Map.of("error", "scheduled_at must be a numeric timestamp (ms since epoch)"));
				}
				scheduledAt = ((Number) raw).longValue();
			}

			if (userId == null || csvPath == null) {
				return ResponseEntity.badRequest().body(Map.of("error", "user_id and csv_path are required"));
			}

			Map<String, Object> batch = batchService.createBatch(userId, csvPath, scheduledAt);

			if (scheduledAt == null) {
				batchProcessor.startBatch((String) batch.get("id"));
				batch = new HashMap<>(batch);
				batch.put("status", "running");
			}

			return ResponseEntity.status(201).body(batch);
		} catch (Exception err) {
			return ResponseEntity.badRequest().body(Map.of("error", err.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getBatch(@PathVariable("id") String id) {
		try {
			Database db = Database.get();
			Map<String, Object> batch = db.get("SELECT * FROM batches WHERE id = ?", id);
			if (batch == null) {
				return ResponseEntity.status(404).body(Map.of("error", "Batch not found"));
			}

			Map<String, Object> calls = db.get("""
				SELECT
				  COUNT(*) as total,
				  SUM(CASE WHEN status = 'pending'     THEN 1 ELSE 0 END) as pending,
				  SUM(CASE WHEN status = 'in_progress' THEN 1 ELSE 0 END) as in_progress,
				  SUM(CASE WHEN status = 'completed'   THEN 1 ELSE 0 END) as completed,
				  SUM(CASE WHEN status = 'failed'      THEN 1 ELSE 0 END) as failed
				FROM calls WHERE batch_id = ?
				""", id);

			Map<String, Object> response = new HashMap<>(batch);
			response.put("calls", calls);
			return ResponseEntity.ok(response);
		} catch (Exception err) {
			return ResponseEntity.status(500).body(Map.of("error", err.getMessage()));
		}
	}

	@GetMapping("/{id}/calls")
	public ResponseEntity<?> getCalls(@PathVariable("id") String id) {
		try {
			Database db = Database.get();
			Map<String, Object> batch = db.get("SELECT * FROM batches WHERE id = ?", id);
			if (batch == null) {
				return ResponseEntity.status(404).body(Map.of("error", "Batch not found"));
			}
			List<Map<String, Object>> calls = db.all(
				"SELECT * FROM calls WHERE batch_id = ? ORDER BY id",
				id
			);
			return ResponseEntity.ok(calls);
		} catch (Exception err) {
			return ResponseEntity.status(500).body(Map.of("error", err.getMessage()));
		}
	}
}
