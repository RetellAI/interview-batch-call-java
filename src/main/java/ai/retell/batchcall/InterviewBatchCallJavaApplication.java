package ai.retell.batchcall;

import ai.retell.batchcall.db.Database;
import ai.retell.batchcall.services.BatchProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InterviewBatchCallJavaApplication {

	public static void main(String[] args) {
		SpringApplication.run(InterviewBatchCallJavaApplication.class, args);
	}

	@Bean
	CommandLineRunner boot() {
		return args -> {
			Database.init();
			BatchProcessor.getInstance().startScheduler();
		};
	}

}
