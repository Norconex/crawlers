package com.norconex.crawler.web.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.util.List;

public class WordCount {
    public static void main(String[] args) {
        final SparkConf sparkConf = new SparkConf().setAppName("WordCountDemo")
                .setMaster("local[*]");

        try (JavaSparkContext sc = new JavaSparkContext(sparkConf)) {
            String filePath = "C:\\Workspace\\Kafka\\apache-zookeeper-3.8.1-bin\\conf\\zoo.cfg";

            JavaRDD<String> rdd = sc.textFile(filePath);
            System.out.println("Num of Partitions: " + rdd.getNumPartitions());

            JavaRDD<String> filteredWords = sc.textFile(filePath)
                    .map(line -> line.replaceAll("[^a-zA-Z\\s]", "").toLowerCase())
                    .flatMap(line -> List.of(line.split("\\s+")).iterator())
                    .filter(word -> (word != null) && word.trim().length() > 0);
            // (word1, the, and, or)
            // (word1, 1),  (the,1), (the,1), and, or) (the,2)
            JavaPairRDD<String, Integer> wordCount = filteredWords.mapToPair(word -> new Tuple2<>(word, 1))
                    .reduceByKey(Integer::sum);

            wordCount.take(10).forEach(System.out::println);

            // Sort by frequency in descending order.
            System.out.println("--------------------------------------------------");
            System.out.println("Top 10 words.");
            //(2, the), (3, word)
            wordCount.mapToPair(tuple -> new Tuple2<>(tuple._2, tuple._1))
                    .sortByKey(false)
                    .take(10)
                    .forEach(tuple -> System.out.printf("(%s, %d)%n", tuple._2, tuple._1));

            SparkUtil.awaitQuitCommand();
        }
    }
}
