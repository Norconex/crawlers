package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.collector.http.robot.RobotsTxt;

/**
 * Resolves and creates "delays" between each document crawled.
 * @author Pascal Essiembre
 */
public interface IDelayResolver extends Serializable  {

	void delay(RobotsTxt robotsTxt, String url);
}
