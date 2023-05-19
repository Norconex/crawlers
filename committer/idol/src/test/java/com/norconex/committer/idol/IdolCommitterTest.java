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
import java.util.Collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Parameters;
import org.mockserver.verify.VerificationTimes;

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
	
	private final String IDOL_DB_NAME = "test";
	
	@TempDir
    static File tempDir;
	
	private static ClientAndServer mockIdol;

	@BeforeAll
	void beforeAll(ClientAndServer client) {
		mockIdol = client;
	}

	@AfterEach
	void afterEach() {
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
		        .isInstanceOf(CommitterException.class)
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
	void testAddTwoDocs_success() throws CommitterException {
		// setup
		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("INDEXID=1"));

		Collection<CommitterRequest> docs = new ArrayList<>();

		Properties metadata1 = new Properties();
		metadata1.add("homer", "simpson");
		CommitterRequest addReq1 = new UpsertRequest(
				"http://thesimpsons.com",
		        metadata1, 
		        null);
		
		Properties metadata2 = new Properties();
		metadata2.add("stewie", "griffin");
		CommitterRequest addReq2 = new UpsertRequest(
				"http://familyguy.com",
		        metadata2, 
		        null);

		docs.add(addReq1);
		docs.add(addReq2);

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
		        
		        #DREREFERENCE http://familyguy.com
		        #DREFIELD stewie="griffin"
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
		assertIdolDeleteRequest(false);
	}
	
	@Test
	void testDeleteTwoDocs_success() throws CommitterException {
		// setup
		mockIdol.when(request().withPath("/DREDELETEREF"))
			.respond(response().withBody("INDEXID=12"));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest deleteReq1 = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());

		CommitterRequest deleteReq2 = new DeleteRequest(
				"http://familyguy.com",
		        new Properties());
		
		docs.add(deleteReq1);
		docs.add(deleteReq2);

		// execute
		withinCommitterSession(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		assertIdolDeleteRequest(true);
	}
	
	@Test
	void testAddOneDoc_customSourceRefFieldWithNoValue_ExceptionThrown() {
		//setup
		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("INDEXID=12"));
		
		Exception expectedException = null;
		
		Collection<CommitterRequest> docs = new ArrayList<>();
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com", null, null);
		docs.add(addReq);
		
		//execute
		try {
			withinCommitterSessionWithCustomSourceRefField(c -> {
				c.commitBatch(docs.iterator());
			});
		} catch(CommitterException e) {
			expectedException = e;
		}
		
		//verify
		assertThat(expectedException)
			.isInstanceOf(CommitterException.class)
			.hasMessage("Source reference field 'myRefField' has no value "
					+ "for document: http://thesimpsons.com");
		
	}
	
	@Test
	void testDeleteOneDoc_customSourceRefFieldWithNoValue_originalDocRefUsed() 
			throws CommitterException {
		//setup
		mockIdol.when(request().withPath("/DREDELETEREF"))
			.respond(response().withBody("INDEXID=12"));
		
		Collection<CommitterRequest> docs = new ArrayList<>();
		CommitterRequest deleteReq = new DeleteRequest(
				"http://thesimpsons.com", new Properties());
		docs.add(deleteReq);
		
		//execute
		withinCommitterSessionWithCustomSourceRefField(c -> {
			c.commitBatch(docs.iterator());
		});
		
		//verify
		assertIdolDeleteRequest(false);
	}
	
	@Test
	void testAddOneDoc_emptyIdolUrl_throwsException() 
			throws CommitterException {
		// setup
		Exception expectedException = null;

		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("INDEXID=132"));

		Collection<CommitterRequest> docs = new ArrayList<>();
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com", null, null);
		docs.add(addReq);

		// execute
		try {
			withinCommitterSessionWithEmptyIdolUrl(c -> {
				c.commitBatch(docs.iterator());
			});
		} catch (IllegalArgumentException e) {
			expectedException = e;
		}

		// verify
		assertThat(expectedException).isNotNull()
		        .isInstanceOf(IllegalArgumentException.class)
		        .hasMessage("Configuration 'url' must be provided.");
	}
	
	@Test
	void testAddOneDoc_wrongIdolUrl_throwsException() 
			throws CommitterException {
		// setup
		Exception expectedException = null;

		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("INDEXID=132"));
				
		Collection<CommitterRequest> docs = new ArrayList<>();
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com", null, null);
		docs.add(addReq);

		// execute
		try {
			withinCommitterSessionWrongIdolUrl(c -> {
				c.commitBatch(docs.iterator());
			});
		} catch (CommitterException e) {
			expectedException = e;
		}

		// verify
		assertThat(expectedException).isNotNull()
		        .isInstanceOf(CommitterException.class)
		        .hasMessage("Cannot post content to http://localhost:1234");
	}
	
	@Test
	void testAddOneDocViaCFS_success() throws CommitterException {
		// setup
		mockIdol.when(
					request()
						.withPath("/")
						.withQueryStringParameter("action", "ingest"))
				.respond(response().withBody(ingestActionResponse()));

		Collection<CommitterRequest> docs = new ArrayList<>();

		Properties metadata = new Properties();
		metadata.add("homer", "simpson");
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com",
		        metadata, 
		        null);

		docs.add(addReq);

		// execute
		withinCommitterSessionCFS(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		String path = "/";
		mockIdol.verify(request()
				.withPath(path)
				.withQueryStringParameter("action", "ingest"), 
				VerificationTimes.exactly(1));
		
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
		
		assertThat(params.getValues("action"))
			.isEqualTo(Collections.singletonList("ingest"));
		
		assertThat(params.getValues("adds"))
			.isEqualTo(Collections.singletonList("""			        
			        <adds><add><document><reference>http://thesimpsons.com</reference><metadata name="homer" value="simpson"></metadata><metadata name="DREDBNAME" value="test"></metadata></document><source content=""></source></add></adds>"""));
	}
	
	@Test
	void testAddOneDocViaCFS_customSourceRefFieldWithNoValue_ExceptionThrown() {
		//setup
		mockIdol.when(
				request()
					.withPath("/")
					.withQueryStringParameter("action", "ingest"))
			.respond(response().withBody("""
			        <autnresponse xmlns:autn='http://schemas.autonomy.com/aci/'>
						<action>INGEST</action>
						<response>SUCCESS</response>
						<responsedata>
							<token>MTAuMi4xMTAuMTQ6NzAwMDpJTkdFU1Q6LTU0MzIyNTEzNQ==</token>
						</responsedata>
					</autnresponse>
			        """));
		
		Exception expectedException = null;
		
		Collection<CommitterRequest> docs = new ArrayList<>();
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com", null, null);
		docs.add(addReq);
		
		//execute
		try {
			withinCommitterSessionCFSWithCustomSourceRefField(c -> {
				c.commitBatch(docs.iterator());
			});
		} catch(CommitterException e) {
			expectedException = e;
		}
		
		//verify
		assertThat(expectedException)
			.isInstanceOf(CommitterException.class)
			.hasMessage("Source reference field 'myRefField' has no value "
					+ "for document: http://thesimpsons.com");
		
	}
	
	@Test
	void testDeleteOneDocViaCFS_success() throws CommitterException {
		// setup
		mockIdol.when(
				request()
					.withPath("/")
					.withQueryStringParameter("action", "ingest"))
				.respond(response().withBody(ingestActionResponse()));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest deleteReq = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());

		docs.add(deleteReq);

		// execute
		withinCommitterSessionCFS(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		String path = "/";
		mockIdol.verify(request()
				.withPath(path)
				.withQueryStringParameter("action", "ingest"), 
				VerificationTimes.exactly(1));
		
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
			.hasSize(3);
		
		assertThat(params.getValues("action"))
			.isEqualTo(Collections.singletonList("ingest"));
		assertThat(params.getValues("removes"))
			.isEqualTo(Collections.singletonList("http://thesimpsons.com"));
		assertThat(params.getValues("DREDbName"))
			.isEqualTo(Collections.singletonList("test"));
	}
	
	@Test
	void testDeleteMultipleDocsViaCFS_success() throws CommitterException {
		// setup
		mockIdol.when(
				request()
					.withPath("/")
					.withQueryStringParameter("action", "ingest"))
				.respond(response().withBody(ingestActionResponse()));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest deleteReqOne = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());
		CommitterRequest deleteReqTwo= new DeleteRequest(
				"http://familyguy.com",
		        new Properties());

		docs.add(deleteReqOne);
		docs.add(deleteReqTwo);

		// execute
		withinCommitterSessionCFS(c -> {
			c.commitBatch(docs.iterator());
		});

		// verify
		String path = "/";
		mockIdol.verify(request()
				.withPath(path)
				.withQueryStringParameter("action", "ingest"), 
				VerificationTimes.exactly(1));
		
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
			.hasSize(3);
		
		assertThat(params.getValues("action"))
			.isEqualTo(Collections.singletonList("ingest"));
		assertThat(params.getValues("removes"))
			.isEqualTo(Collections.singletonList(
					"http://thesimpsons.com,http://familyguy.com"));
		assertThat(params.getValues("DREDbName"))
			.isEqualTo(Collections.singletonList("test"));
	}
	
	@Test
	void testDeleteOneDocViaCFS_customSourceRefFieldWithNoValue_originalDocRefUsed() 
			throws CommitterException {
		// setup		
		mockIdol.when(
				request()
					.withPath("/")
					.withQueryStringParameter("action", "ingest"))
				.respond(response().withBody(ingestActionResponse()));

		Collection<CommitterRequest> docs = new ArrayList<>();

		CommitterRequest deleteReq = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());

		docs.add(deleteReq);

		// execute
		withinCommitterSessionCFSWithCustomSourceRefField(c -> {
			c.commitBatch(docs.iterator());
		});
	
		// verify
		String path = "/";
		mockIdol.verify(
				request()
					.withPath(path)
					.withQueryStringParameter("action", "ingest"), 
				VerificationTimes.exactly(1));		
				
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
			.hasSize(3);
		
		assertThat(params.getValues("action"))
			.isEqualTo(Collections.singletonList("ingest"));
		assertThat(params.getValues("removes"))
			.isEqualTo(Collections.singletonList(
					"http://thesimpsons.com"));
		assertThat(params.getValues("DREDbName"))
			.isEqualTo(Collections.singletonList("test"));
	}
	
	@Test
    void test2AddsAnd2Deletes_success() throws Exception{
		//setup
		mockIdol.when(request().withPath("/DREADDDATA"))
				.respond(response().withBody("INDEXID=54"));

		mockIdol.when(request().withPath("/DREDELETEREF"))
			.respond(response().withBody("INDEXID=55"));
		
		Collection<CommitterRequest> docs = new ArrayList<>();

		Properties metadata1 = new Properties();
		metadata1.add("homer", "simpson");
		CommitterRequest addReq1 = new UpsertRequest(
				"http://thesimpsons.com",
		        metadata1, 
		        null);
		
		Properties metadata2 = new Properties();
		metadata2.add("stewie", "griffin");
		CommitterRequest addReq2 = new UpsertRequest(
				"http://familyguy.com",
		        metadata2, 
		        null);

		CommitterRequest deleteReq1 = new DeleteRequest(
				"http://thesimpsons.com",
		        new Properties());

		CommitterRequest deleteReq2 = new DeleteRequest(
				"http://familyguy.com",
		        new Properties());
		
		docs.add(addReq1);
		docs.add(deleteReq1);
		docs.add(addReq2);		
		docs.add(deleteReq2);

		// execute
		withinCommitterSession(c -> {
			c.commitBatch(docs.iterator());
		});
        
		//verify
		mockIdol.verify(request()
				.withPath("/DREDELETEREF"), VerificationTimes.exactly(2));
		
		mockIdol.verify(request()
				.withPath("/DREADDDATA"), VerificationTimes.exactly(2));
    }

	@Test
    void testAddDoc_MultiValueFields() throws Exception {
		//setup
		mockIdol.when(request().withPath("/DREADDDATA"))
			.respond(response().withBody("INDEXID=1"));

		Collection<CommitterRequest> docs = new ArrayList<>();
		
		Properties metadata = new Properties();
		metadata.set("homer", "simpson", "cartoon");
		CommitterRequest addReq = new UpsertRequest(
				"http://thesimpsons.com",
		        metadata, 
		        null);
		
		docs.add(addReq);
		
		// execute
		withinCommitterSession(c -> {
			c.commitBatch(docs.iterator());
		});
		
		//verify
		String path = "/DREADDDATA";
		mockIdol.verify(request()
				.withPath(path), VerificationTimes.exactly(1));
		
		HttpRequest[] request = 
				mockIdol.retrieveRecordedRequests(
						HttpRequest.request()
						.withPath(path)
						.withMethod("POST"));
		
		assertThat(request).hasSize(1);
		assertThat(request[0].getBodyAsString()).contains("""
		        #DREFIELD homer="simpson"
		        #DREFIELD homer="cartoon"
		        """);
	}
	
	private void assertIdolDeleteRequest(boolean twoDocs) {
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
		
		String docRefs = "http://thesimpsons.com";
		if(twoDocs) {
			docRefs = docRefs + " http://familyguy.com";
		}
		assertThat(params.getValues("Docs"))
			.isEqualTo(Collections.singletonList(docRefs));
		
		assertThat(params.getValues("DREDbName"))
		.isEqualTo(Collections.singletonList(IDOL_DB_NAME));
	}
	
	private CommitterContext createIdolCommitterContext() {
		CommitterContext ctx = CommitterContext.builder()
                .setWorkDir(new File(tempDir,
                        "" + TimeIdGenerator.next()).toPath())
                .build();
		return ctx;
	}
	
	private IdolCommitter createIdolCommitterNoInitContext() 
			throws CommitterException {
        IdolCommitter committer = new IdolCommitter();
        committer.getConfig().setUrl(
        		"http://localhost:" + mockIdol.getLocalPort());
        committer.getConfig().setDatabaseName(IDOL_DB_NAME);
        return committer;
    }

    private IdolCommitter withinCommitterSession(CommitterConsumer c)
            throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.init(createIdolCommitterContext());
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
    
    private IdolCommitter withinCommitterSessionWithCustomSourceRefField(
    		CommitterConsumer c) throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.getConfig().setSourceReferenceField("myRefField");
        committer.init(createIdolCommitterContext());
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
    
    private IdolCommitter withinCommitterSessionWithEmptyIdolUrl(
    		CommitterConsumer c) throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.getConfig().setUrl("");
        committer.init(createIdolCommitterContext());
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
    
    private IdolCommitter withinCommitterSessionWrongIdolUrl(
    		CommitterConsumer c) throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.getConfig().setUrl("http://localhost:1234");
        committer.init(createIdolCommitterContext());
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
    
    private IdolCommitter withinCommitterSessionCFS(CommitterConsumer c)
            throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.getConfig().setCfs(true);
        committer.init(createIdolCommitterContext());
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
    
    private IdolCommitter withinCommitterSessionCFSWithCustomSourceRefField(
    		CommitterConsumer c)
            throws CommitterException {
        IdolCommitter committer = createIdolCommitterNoInitContext();
        committer.getConfig().setCfs(true);
        committer.getConfig().setSourceReferenceField("myRefField");
        committer.init(createIdolCommitterContext());
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
    
    private String ingestActionResponse() {
    	return """
    	        <autnresponse xmlns:autn='http://schemas.autonomy.com/aci/'>
					<action>INGEST</action>
					<response>SUCCESS</response>
					<responsedata>
						<token>MTAuMi4xMTAuMTQ6NzAwMDpJTkdFU1Q6LTU0MzIyNTEzNQ==</token>
					</responsedata>
				</autnresponse>
    	        """;
    }
}
