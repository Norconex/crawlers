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
<title>Login</title>
<style type="text/css">
<!--
body {
  font-family: Verdana, arial, sans-serif;
  color: black;
  font-size: 12px;
}
-->
</style>
</head>
<body>
	<h1>OpenCMIS Repository Login</h1>

	<div>
	<p>Please provide your credentials for this CMIS repository.</p>
	<p>
		<% if (request.getAttribute("org.apache.chemistry.opencmis.error") != null) { %>
		<span style="color: red"><%= request.getAttribute("org.apache.chemistry.opencmis.error") %></span>
		<% } %>
	</p>
	</div>

	<div>
	<form method="POST">
		<table>
			<tr>
				<td>Username:</td>
				<td><input type="text" name="user" size="20"></td>
			</tr>
			<tr>
				<td>Password:</td>
				<td><input type="password" name="password" size="20" autocomplete="off"/></td>
			</tr>
			<tr>
				<td></td>
				<td><input type="checkbox" name="trustapp" value="1">
				    I'm trusting this application:<br>
				    <span style="font-weight: bold"><%= request.getAttribute("org.apache.chemistry.opencmis.appurl") %></span>
				</td>
			</tr>
			<tr>
				<td></td>
				<td><input type="submit" value="Login"> <input type="button" value="Cancel" onClick="window.history.back()"></td>
			</tr>

		</table>
		<input type="hidden" name="key" value="<%= request.getAttribute("org.apache.chemistry.opencmis.formkey") %>">
	</form>
	</div>
	
</body>
</html>