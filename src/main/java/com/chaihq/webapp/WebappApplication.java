package com.chaihq.webapp;

import com.chaihq.webapp.config.DotEnvConfig;
import com.chaihq.webapp.storage.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class WebappApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(WebappApplication.class);
		app.addInitializers(new DotEnvConfig());
		app.run(args);
	}


}
