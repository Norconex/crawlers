<%@ page language="java" contentType="application/json; charset=UTF-8" pageEncoding="UTF-8"%>
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

function OpenCMISConnector()  {
	this.repositoryDomain  = '<%= request.getAttribute("org.apache.chemistry.opencmis.domain") %>';
	this.serviceUrl        = '<%= request.getAttribute("org.apache.chemistry.opencmis.serviceUrl") %>';
	this.iframeUrl         = '<%= request.getAttribute("org.apache.chemistry.opencmis.iframeUrl") %>';
	this.applicationDomain = '';
	this.iframe   = null;
	this.init     = false;
	this.appKey   = '';
	this.loginKey = '';
	this.loginCallback  = null;
	this.logoutCallback = null;
	this.tokenCallbacks = new Array();
}

OpenCMISConnector.prototype.cmisInit = function() {
	// get login key cookie
	var ca = document.cookie.split(';');
	for (var i = 0; i < ca.length; i++) {
		var c = ca[i].replace(/^\s\s*/, '').replace(/\s\s*$/, '');
		if (c.indexOf('opencmis_loginkey=') == 0) {
			this.loginKey = unescape(c.substring(18));
			break;
		}
	}

	if (this.loginKey != '') {
		// remove the cookie
		document.cookie = 'opencmis_loginkey=_; Max-Age=0; expires=Thu, 01-Jan-70 00:00:01 GMT; Version="1"';
	}

	// app domain and path
	this.applicationDomain = window.location.protocol + '//' + window.location.host;
	var appDomainEncoded = encodeURIComponent(this.applicationDomain);
	var appPathEncoded = encodeURIComponent(window.location.pathname + window.location.search);

	var self = this;

	// register message event listener
	window.addEventListener('message', function(event) { self.cmisReceiver(event) }, false);

	// set up iframe
	var repositoryIframe = document.createElement('iframe');
	repositoryIframe.src = this.iframeUrl + (this.iframeUrl.indexOf('?') == -1 ? '?' : '&') + 'domain=' + appDomainEncoded + '&path=' + appPathEncoded;
	repositoryIframe.style.display = 'none';

	//var frameLoginKey = this.loginKey;
	//var frameRepositoryDomain = this.repositoryDomain;
	repositoryIframe.onload = function() { self.sendLoginMessage(); };

	this.iframe = document.getElementsByTagName('body').item(0).appendChild(repositoryIframe);

	this.init = true;
};

OpenCMISConnector.prototype.cmisReceiver = function(e) {
	if (e.origin != this.repositoryDomain) {
		// ignore messages that have not been sent from the repository domain
		return;
	}

	if (e.data.substring(0, 6) == 'token:') {
		// response of a token request, repository sends token

		if (this.tokenCallbacks.length > 0) {
			// at least one callback is waiting for a token

			// extract token
			var token = e.data.substring(6);

			// trigger callback
			this.tokenCallbacks.pop()(token);
		}
	} else if (e.data.substring(0, 7) == 'appkey:') {
		if (this.loginCallback != null) {
			var callback = this.loginCallback;
			this.loginCallback = null;

			this.appKey = e.data.substring(7, 67);
			this.loginKey = '';

			callback(true);
		}
	} else if (e.data.substring(0, 9) == 'loginkey:') {
		if (this.loginCallback != null) {
			var callback = this.loginCallback;
			this.loginCallback = null;

			this.loginKey = e.data.substring(9, 69);

			var loginUrl = e.data.substring(70);

			var expires = new Date();
			expires.setTime(expires.getTime() + 3600);

			document.cookie = 'opencmis_loginkey=' + escape(this.loginKey)
			+ '; Max-Age=3600; expires=' + expires.toGMTString()
			+ '; Version="1"; Discard';

			window.location.href = loginUrl;
		}
	} else if (e.data.substring(0, 7) == 'logout:') {
		if (this.logoutCallback != null) {
			var callback = this.logoutCallback;
			this.logoutCallback = null;

			var success = ('ok' == e.data.substring(7));

			callback(success);
		}
	}
};

OpenCMISConnector.prototype.sendLoginMessage = function() {
	this.iframe.contentWindow.postMessage('login:' + this.loginKey, this.repositoryDomain);
};

OpenCMISConnector.prototype.cmisServiceURL = function() {
	return this.serviceUrl;
};

OpenCMISConnector.prototype.cmisLogin = function(callback) {
	if (this.loginCallback != null) {
		// there is already a login in progress
		callback(false);
		return;
	}

	this.loginCallback = callback;

	if (!this.init) {
		this.cmisInit();
	} else {
		sendLoginMessage();
	}
};

OpenCMISConnector.prototype.cmisLogout = function(callback) {
	if (this.logoutCallback != null) {
		// there is already a logout in progress
		callback(false);
		return;
	}

	if (!this.init) {
		// there hasn't been a login before
		callback(false);
		return;
	}

	this.logoutCallback = callback;
	this.iframe.contentWindow.postMessage('logout:' + this.appKey, this.repositoryDomain);
};

OpenCMISConnector.prototype.cmisNextToken = function(callback) {
	if (!this.init) {
		// not logged in
		callback('');
		return;
	}

	this.tokenCallbacks.unshift(callback);
	this.iframe.contentWindow.postMessage('token:' + this.appKey, this.repositoryDomain);
};

var openCMISConnector = new OpenCMISConnector();


// Functions defined by the CMIS 1.1 specification

function cmisServiceURL() {
	return openCMISConnector.cmisServiceURL();
}

function cmisLogin(callback) {
	openCMISConnector.cmisLogin(callback);
}

function cmisLogout(callback) {
	openCMISConnector.cmisLogout(callback);
}

function cmisNextToken(callback) {
	openCMISConnector.cmisNextToken(callback);
}