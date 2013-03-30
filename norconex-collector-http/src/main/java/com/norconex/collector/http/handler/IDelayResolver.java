package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.collector.http.robot.RobotsTxt;

/**
 * Resolves and creates "delays" between each document crawled.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDelayResolver extends Serializable  {

	void delay(RobotsTxt robotsTxt, String url);
}
