package ir.h0p3.securebankapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SecureBankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureBankApiApplication.class, args);
    }

}
