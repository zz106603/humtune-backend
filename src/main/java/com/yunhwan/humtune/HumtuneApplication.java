package com.yunhwan.humtune;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableAsync
@EnableJpaAuditing
@SpringBootApplication
public class HumtuneApplication {

	public static void main(String[] args) {
		SpringApplication.run(HumtuneApplication.class, args);
	}

}
