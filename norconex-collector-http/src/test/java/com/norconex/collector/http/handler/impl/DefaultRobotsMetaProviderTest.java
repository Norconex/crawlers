package com.norconex.collector.http.handler.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Test;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.robot.RobotsMeta;

public class DefaultRobotsMetaProviderTest {

    @Test
    public void testRobotsMetaProvider() throws IOException {
        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "DefaultRobotsMetaProviderTest.html"));
        String docURL = "http://www.example.com/DefaultURLExtractorTest.html";
        HttpMetadata metadata = new HttpMetadata(docURL);
        metadata.setString(HttpMetadata.HTTP_CONTENT_TYPE, "text/html");

        DefaultRobotsMetaProvider p = new DefaultRobotsMetaProvider();
        RobotsMeta robotsMeta = p.getRobotsMeta(
                docReader, docURL, metadata.getContentType(), metadata);
        
        assertTrue("Robots meta should be noindex nofollow.", 
                robotsMeta.isNofollow() && robotsMeta.isNoindex());
    }
}
