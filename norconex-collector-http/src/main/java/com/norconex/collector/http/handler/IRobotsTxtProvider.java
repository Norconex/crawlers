package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;

import com.norconex.collector.http.robot.RobotsTxt;


//TODO have a generic RobotsTXT class instead that has all rules + directives
//so it can be considered across the board (passed in many methods)?


/**
 * Given a URL, extract any "robots.txt" rules.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IRobotsTxtProvider extends Serializable {

   
    RobotsTxt getRobotsTxt(HttpClient httpClient, String url);
    
}
