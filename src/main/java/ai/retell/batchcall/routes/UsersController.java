package ai.retell.batchcall.routes;

import ai.retell.batchcall.db.Database;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UsersController {
	@GetMapping
	public ResponseEntity<?> listUsers() {
		try {
			Database db = Database.get();
			List<Map<String, Object>> users = db.all("SELECT * FROM users");
			return ResponseEntity.ok(users);
		} catch (Exception err) {
			return ResponseEntity.status(500).body(Map.of("error", err.getMessage()));
		}
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getUser(@PathVariable("id") String id) {
		try {
			Database db = Database.get();
			Map<String, Object> user = db.get("SELECT * FROM users WHERE id = ?", id);
			if (user == null) {
				return ResponseEntity.status(404).body(Map.of("error", "User not found"));
			}
			return ResponseEntity.ok(user);
		} catch (Exception err) {
			return ResponseEntity.status(500).body(Map.of("error", err.getMessage()));
		}
	}
}
