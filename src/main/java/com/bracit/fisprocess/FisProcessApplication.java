package com.bracit.fisprocess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FisProcessApplication {

  public static void main(String[] args) {
    SpringApplication.run(FisProcessApplication.class, args);
  }

}
