package ai.retell.batchcall.routes;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.services.CallSimulator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/failures")
public class TestController {
	@PostMapping
	public ResponseEntity<?> updateFailures(@RequestBody Map<String, Object> body) {
		Database db = Database.get();
		CallSimulator callSim = CallSimulator.getInstance();

		if (body.containsKey("db_failure_rate")) {
			Object raw = body.get("db_failure_rate");
			if (raw instanceof Number) {
				db.setFailureRate(((Number) raw).doubleValue());
			}
		}
		if (body.containsKey("call_failure_rate")) {
			Object raw = body.get("call_failure_rate");
			if (raw instanceof Number) {
				callSim.setCallFailureRate(((Number) raw).doubleValue());
			}
		}
		if (Boolean.TRUE.equals(body.get("force_next_db_failure"))) {
			db.forceNextFailure();
		}
		if (Boolean.TRUE.equals(body.get("force_next_call_failure"))) {
			callSim.forceNextCallFailure();
		}

		Map<String, Object> response = new HashMap<>();
		response.put("db_failure_rate", db.getFailureRate());
		response.put("call_failure_rate", callSim.getCallFailureRate());
		return ResponseEntity.ok(response);
	}

	@GetMapping
	public ResponseEntity<?> getFailures() {
		Database db = Database.get();
		CallSimulator callSim = CallSimulator.getInstance();
		return ResponseEntity.ok(Map.of(
			"db_failure_rate", db.getFailureRate(),
			"call_failure_rate", callSim.getCallFailureRate()
		));
	}
}
