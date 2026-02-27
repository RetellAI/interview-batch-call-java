package ai.retell.batchcall.db;

public class DatabaseError extends RuntimeException {
	public DatabaseError(String message) {
		super(message);
	}
}
