package com.deolle.tldrbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TldrBotApplication {

	public static void main(String[] args) {
		SpringApplication.run(TldrBotApplication.class, args);
	}

}
