package com.norconex.crawler.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;

@SpringBootApplication(
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
@ComponentScan(
    basePackages = {"com.norconex.crawler.server"},
    nameGenerator = FullyQualifiedAnnotationBeanNameGenerator.class
)
public class CrawlServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrawlServerApplication.class, args);
    }
}