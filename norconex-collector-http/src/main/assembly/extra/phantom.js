/* Copyright 2017 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
var page = require('webpage').create();
var fs = require('fs');
var system = require('system');

if (system.args.length !== 9) {
	system.stderr.writeLine('Invalid number of arguments.');
    phantom.exit(1);
}

var url = system.args[1];           // The URL to fetch
var outfile = system.args[2];       // The temp output file
var timeout = system.args[3];       // How long to wait for a page to render
var bindId = system.args[4];        // HttpClient binding id
var protocol = system.args[5];      // Was the original URL "https" or "http"?
var thumbnailFile = system.args[6]; // Optional path to image file
var dimension = system.args[7];     // e.g. 1024x768
var zoomFactor = system.args[8];    // e.g. 0.25 (25%)

if (thumbnailFile && dimension) {
	var pageWidth = 1024;
	var pageHeight = 768;
    if (dimension) {
    	var size = dimension.split('x');
        pageWidth = parseInt(size[0], 10) * zoomFactor;
        pageHeight = parseInt(size[1], 10) * zoomFactor;
    }
	page.viewportSize = { width: pageWidth, height: pageHeight };
	page.clipRect = { top: 0, left: 0, width: pageWidth, height: pageHeight };
}
if (thumbnailFile && zoomFactor) {
	page.zoomFactor = zoomFactor;
}
if (bindId !== "-1") {
    page.customHeaders = {
        "collector.proxy.bindId": bindId,
        "collector.proxy.protocol": protocol
    };
}

page.onResourceError = function(resourceError) {
	system.stderr.writeLine(
			resourceError.url + ': ' + resourceError.errorString);
};
page.onResourceReceived = function(response) {
    if (response.url === url) { 
        response.headers.forEach(function(header){
            system.stdout.writeLine(
            		'HEADER:' + header.name + '=' + header.value);
        });
        system.stdout.writeLine('STATUS:' + response.status);
        system.stdout.writeLine('STATUSTEXT:' + response.statusText);
        system.stdout.writeLine('CONTENTTYPE:' + response.contentType);
    }	
};

page.open(url, function (status) {
    if (status !== 'success') {
    	system.stderr.writeLine('Unable to load: ' + url + ' (status=' + status + ').');
    	system.stderr.writeLine('Content: ' + page.content);
        phantom.exit();
    } else {
        window.setTimeout(function () {
        	if (thumbnailFile) {
                page.render(thumbnailFile);
        	}
            fs.write(outfile, page.content, 'w');
            phantom.exit();
        }, timeout);
    }
});


/*

page.onResourceRequested = function (request) {
    system.stderr.writeLine('= onResourceRequested()');
    system.stderr.writeLine('  request: ' + JSON.stringify(request, undefined, 4));
};

page.onResourceReceived = function(response) {
    system.stderr.writeLine('= onResourceReceived()' );
    system.stderr.writeLine('  id: ' + response.id + ', stage: "' + response.stage + '", response: ' + JSON.stringify(response));
};

page.onLoadStarted = function() {
    system.stderr.writeLine('= onLoadStarted()');
    var currentUrl = page.evaluate(function() {
        return window.location.href;
    });
    system.stderr.writeLine('  leaving url: ' + currentUrl);
};

page.onLoadFinished = function(status) {
    system.stderr.writeLine('= onLoadFinished()');
    system.stderr.writeLine('  status: ' + status);
};

page.onNavigationRequested = function(url, type, willNavigate, main) {
    system.stderr.writeLine('= onNavigationRequested');
    system.stderr.writeLine('  destination_url: ' + url);
    system.stderr.writeLine('  type (cause): ' + type);
    system.stderr.writeLine('  will navigate: ' + willNavigate);
    system.stderr.writeLine('  from page\'s main frame: ' + main);
};

page.onResourceError = function(resourceError) {
    system.stderr.writeLine('= onResourceError()');
    system.stderr.writeLine('  - unable to load url: "' + resourceError.url + '"');
    system.stderr.writeLine('  - error code: ' + resourceError.errorCode + ', description: ' + resourceError.errorString );
};

page.onError = function(msg, trace) {
    system.stderr.writeLine('= onError()');
    var msgStack = ['  ERROR: ' + msg];
    if (trace) {
        msgStack.push('  TRACE:');
        trace.forEach(function(t) {
            msgStack.push('    -> ' + t.file + ': ' + t.line + (t.function ? ' (in function "' + t.function + '")' : ''));
        });
    }
    system.stderr.writeLine(msgStack.join('\n'));
};

*/