package br.com.smartberry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartberryApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartberryApplication.class, args);
	}

}
