package com.chaihq.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class WebappApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(WebappApplication.class, args);
	}


}
