package com.norconex.crawler.web.spark;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.StreamingQueryException;

import java.util.concurrent.TimeoutException;

public class StreamingFromTableDemo {
    public static void main(String[] args) throws TimeoutException, StreamingQueryException {
        SparkSession spark = SparkUtil.createSparkSession("StreamingFromTableDemo");

        // Check the table result
        Dataset<Row> jdbcDF = spark.read()
                .format("jdbc")
                .option("url", "jdbc:mysql://localhost:3306/test")
                .option("user", "root")
//                .option("password", "12345")
                .option("driver", "com.mysql.cj.jdbc.Driver")
//                .table("CIPCODEDESC");

        .option("dbtable","document")
                .load();

        // Check the new table result
        jdbcDF.show();
    }
}
