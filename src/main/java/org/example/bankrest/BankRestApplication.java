package org.example.bankrest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankRestApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankRestApplication.class, args);
	}

}
