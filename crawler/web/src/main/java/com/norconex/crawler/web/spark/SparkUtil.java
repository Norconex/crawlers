package com.norconex.crawler.web.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.SparkSession;

import java.util.Scanner;

public class SparkUtil {
    public static SparkConf createSparkConf(String name) {
        return new SparkConf().setAppName(name)
                .setMaster("local[*]");
    }

    public static SparkConf createSparkConf(String name, String master) {
        return new SparkConf().setAppName(name)
                .setMaster(master);
    }

    public static JavaSparkContext createSparkConf(String name, String[] args) {
        SparkSession.Builder builder = SparkSession.builder().appName(name);

        if (args.length == 2) {
            builder.master(args[1]);
        }

        return new JavaSparkContext(builder.getOrCreate().sparkContext());
    }

    public static SparkSession createSparkSession(String name) {
        return SparkSession
                .builder()
                .appName(name)
                .master("local[*]")
                .getOrCreate();
    }

    public static JavaSparkContext createSparkConfFromSparkSession(String name) {
        return new JavaSparkContext(SparkSession.builder()
                .appName(name)
                .getOrCreate().sparkContext());
    }


    public static void awaitQuitCommand() {
        while (true) {
            System.out.print("Enter 'quit' to exit program: ");
            String command = new Scanner(System.in).next();
            if (command.equalsIgnoreCase("quit")) {
                System.exit(1);
            }
        }
    }

    public static void waitForSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
