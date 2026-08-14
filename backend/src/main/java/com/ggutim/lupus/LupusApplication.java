package com.ggutim.lupus;

import com.ggutim.lupus.room.GameRules;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(GameRules.class)
public class LupusApplication {

	public static void main(String[] args) {
		SpringApplication.run(LupusApplication.class, args);
	}

}
