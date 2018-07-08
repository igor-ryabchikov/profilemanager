package ru.ifmo.smartcity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Created by igor on 06.07.18.
 */
@SpringBootApplication
@ComponentScan
public class ProfileManagerMain {

    public static void main(String[] args) {
        SpringApplication.run(ProfileManagerMain.class, args);
    }

}
