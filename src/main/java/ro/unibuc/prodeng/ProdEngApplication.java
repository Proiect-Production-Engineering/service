package ro.unibuc.prodeng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableMongoRepositories
public class ProdEngApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProdEngApplication.class, args);
	}
}
