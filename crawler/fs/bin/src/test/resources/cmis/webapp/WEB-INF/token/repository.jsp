<%@ page language="java" contentType="text/html; charset=utf-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<!--
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
-->
<html>
<head>
<meta charset="UTF-8">
<title>Repository</title>
</head>
<body>
	<script src="http://code.jquery.com/jquery-1.7.2.min.js"></script>
	<script type="text/javascript">
		var appInfo = getAppInfo();
		var appDomain = appInfo[0];
		var appPath = appInfo[1]

		// tokens in stock, initially empty
		var tokens = new Array();

		if (window.top != window.self && appDomain != null && appPath != null) {
			// add message event listener
			window.addEventListener('message', receiver, false);
		}

		//////////////////////////////////////////////////////////
		// Event listener callback.
		//////////////////////////////////////////////////////////
		function receiver(e) {
			if (e.origin != appDomain) {
				// ignore messages that have not been sent from the application domain
				return;
			}

			if (e.data.substring(0, 5) == 'token') {
				var appKey = e.data.substring(6);

				if (tokens.length > 0) {
					// we have a least one token in stock -> remove one and send it
					e.source.postMessage('token:' + tokens.pop(), e.origin);
				} else {
					// we are out of tokens -> ask the server for another batch
					requestTokens(e, appKey);
				}
			} else if (e.data.substring(0, 5) == 'login') {
				var loginKey = e.data.substring(6);
				login(e, loginKey);
			} else if (e.data.substring(0, 6) == 'logout') {
				var appKey = e.data.substring(7);
				logout(e, appKey);
			}
		}

		function login(e, loginKey) {
			$.ajax({
				url : '<%= request.getAttribute("org.apache.chemistry.opencmis.loginUrl") %>login',
				type : 'POST',
				data : {
					url : appDomain + appPath,
					key : loginKey
				},
				success : function(data) {
					if (data.ok == 1) {
						e.source.postMessage('appkey:' + data.key, e.origin);
					} else {
						e.source.postMessage('loginkey:' + data.key + ":"
								+ data.url, e.origin);
					}
				},
				error : function(msg) {
					alert("Error: " + msg);
				}
			});
		}

		function logout(e, appKey) {
			$.ajax({
				url : '<%= request.getAttribute("org.apache.chemistry.opencmis.loginUrl") %>logout',
				type : 'POST',
				data : {
					key : appKey
				},
				success : function(data) {
					if (data.ok == 1) {
						e.source.postMessage('logout:ok', e.origin);
					} else {
						e.source.postMessage('logout:failure', e.origin);
					}
				},
				error : function(msg) {
					alert("Error: " + msg);
				}
			});
		}

		//////////////////////////////////////////////////////////
		// Requests new tokens from the server.
		//////////////////////////////////////////////////////////
		function requestTokens(e, appKey) {
			$.ajax({
				url : '<%= request.getAttribute("org.apache.chemistry.opencmis.loginUrl") %>token',
				type : 'POST',
				data : {
					key : appKey
				},
				success : function(data) {

					// sanity check: we expect an array
					if ($.isArray(data)) {

						// if we received more than one token, put them in stock except for the first one
						if (data.length > 1) {
							for ( var i = 1; i < data.length; i++) {
								var token = data[i];
								tokens.push(token);
							}
						}

						// if the array wasn't empty, send the first token to the applictaion
						if (data.length > 0) {
							e.source.postMessage('token:' + data[0], e.origin);
						}
					}
				}
			});
		}

		function getAppInfo() {
			var domain = null;
			var path = null;
			var frameHref = window.location.href;

			var params = frameHref.slice(frameHref.indexOf('?') + 1).split('&');
			for ( var i = 0; i < params.length; i++) {
				var keyValue = params[i].split('=');
				if (keyValue[0] == 'domain') {
					domain = decodeURIComponent(keyValue[1]);
				} else if (keyValue[0] == 'path') {
					path = decodeURIComponent(keyValue[1]);
				}
			}

			return [ domain, path ];
		}
	</script>

</body>
</html>