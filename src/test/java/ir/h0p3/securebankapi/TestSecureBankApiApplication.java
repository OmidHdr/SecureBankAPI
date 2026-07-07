package ir.h0p3.securebankapi;

import org.springframework.boot.SpringApplication;

public class TestSecureBankApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(SecureBankApiApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
