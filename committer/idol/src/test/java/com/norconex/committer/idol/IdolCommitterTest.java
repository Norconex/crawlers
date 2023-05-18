/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.committer.idol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameter;
import org.mockserver.model.Parameters;
import org.mockserver.verify.VerificationTimes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.map.Properties;

/**
 * IdolCommitter main tests.
 *
 * @author Harinder Hanjan
 */
@MockServerSettings
@TestInstance(Lifecycle.PER_CLASS)
class IdolCommitterTest {
	@TempDir
    static File tempDir;
	
	private static ClientAndServer mockIdol;

	@BeforeAll
	void beforeAll(ClientAndServer client) {
		mockIdol = client;
	}

	@BeforeEach
	void beforeEach() {
		mockIdol.reset();
	}

	@Test
	void testAddOneDoc_IdolReturnsUnexpectedResponse_exceptionThrown() {
		// setup
		Exception expectedException = null;

		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("NOOP"));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com", null, null);

		docs.add(addReq);

		// execute
		try {
			withinCommitterSession(c -> {
				c.commitBatch(docs.iterator());
			});
		} catch (CommitterException e) {
			expectedException = e;
		}

		// verify
		assertThat(expectedException).isNotNull()
		        .isOfAnyClassIn(CommitterException.class)
		        .hasMessageStartingWith("Unexpected HTTP response: ");
	}
	
	@Test
	void testAddOneDoc_success() throws CommitterException {
		// setup
		mockIdol.when(request().withPath("/DREADDDATA"))
				.respond(response().withBody("INDEXID=1"));

		Collection<CommitterRequest> docs = new ArrayList<>();

		Properties metadata = new Properties();
		metadata.add("homer", "simpson");
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com",
		        metadata, 
		        null);

		docs.add(addReq);

		// execute
		withinCommitterSession(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		String path = "/DREADDDATA";
		mockIdol.verify(request()
				.withPath(path), VerificationTimes.exactly(1));
		
		HttpRequest[] request = 
				mockIdol.retrieveRecordedRequests(
						HttpRequest.request()
						.withPath(path)
						.withMethod("POST"));
		
		assertThat(request).hasSize(1);
		assertThat(request[0].getBodyAsString()).isEqualTo("""

		        #DREREFERENCE http://thesimpsons.com
		        #DREFIELD homer="simpson"
		        #DREDBNAME test
		        #DRECONTENT

		        #DREENDDOC

		        #DREENDDATANOOP

		        """);
	}
	
	@Test
	void testDeleteOneDoc_success() throws CommitterException {
		// setup
		mockIdol.when(request().withPath("/DREDELETEREF"))
				.respond(response().withBody("INDEXID=12"));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest deleteReq = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());

		docs.add(deleteReq);

		// execute
		withinCommitterSession(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		String path = "/DREDELETEREF";
		mockIdol.verify(request()
				.withPath(path), VerificationTimes.exactly(1));
		
				
		HttpRequest[] request = 
				mockIdol.retrieveRecordedRequests(
						HttpRequest.request()
						.withPath(path)
						.withMethod("POST"));
		
		assertThat(request).hasSize(1);
		
		Parameters params = request[0].getQueryStringParameters();
		assertThat(params).isNotNull();
		assertThat(params.getEntries())
			.isNotNull()
			.hasSize(2);
		
		Parameter firstParam = params.getEntries().get(0);
		assertThat(firstParam.getName().getValue()).isEqualTo("Docs");
		assertThat(firstParam.getValues().get(0).getValue())
			.isEqualTo("http://thesimpsons.com");		
	}
	
	private IdolCommitter createIdolCommitter() throws CommitterException {
        CommitterContext ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
        IdolCommitter committer = new IdolCommitter();
        committer.getConfig().setUrl("http://localhost:" + mockIdol.getLocalPort());
        committer.getConfig().setDatabaseName("test");
        committer.init(ctx);
        return committer;
    }

    private IdolCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        IdolCommitter committer = createIdolCommitter();
        try {
            c.accept(committer);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(e);
        }
        committer.close();
        return committer;
    }
    
    @FunctionalInterface
    private interface CommitterConsumer {
        void accept(IdolCommitter c) throws Exception;
    }
}