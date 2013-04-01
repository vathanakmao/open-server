package com.wadpam.open.web;

import com.wadpam.open.json.JComplex;
import com.wadpam.open.json.JSample;
import java.net.URI;
import java.util.logging.Level;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Integration test for the CRUD controllers.
 * @author sosandtrom
 */
public class CrudITest {
    
    static final Logger LOG = LoggerFactory.getLogger(CrudITest.class);

    static final String                  BASE_URL       = "http://localhost:8234/domain/itest/";

    RestTemplate                         template;
    public CrudITest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
        template = new RestTemplate();
        LOG.info("========================= setUp() ============================");
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testCreateSample() {
        LOG.info("------------------- testCreateSample() -----------------------");
        final String NAME = "mySampleName";
        JSample actual = createSample(NAME);
        assertNotNull("Assigned Sample ID", actual.getId());
        assertEquals("Created name", NAME, actual.getName());
    }
    
    protected JSample createSample(String name) {
        JSample request = new JSample();
        request.setName(name);
        URI uri = template.postForLocation(BASE_URL + "sample/v10", request);
        
        try {
            JSample actual = template.getForObject(uri, JSample.class);
            return actual;
        }
        catch (HttpServerErrorException serverSide) {
            LOG.error("server problems", serverSide.getRootCause());
            throw serverSide;
        }
    }

    @Test
    public void testCreateComplex() {
        LOG.info("------------------- testCreateComplex() -----------------------");
        final String ORG_NAME = "myOrganizationName";
        JSample org = createSample(ORG_NAME);
        
        final String NAME = "myComplexName";
        JComplex request = new JComplex();
        request.setName(NAME);
        request.setManagerId(null);
        request.setOrganizationId(Long.parseLong(org.getId()));
        URI uri = template.postForLocation(BASE_URL + "complex/v10", request);

        // TODO: enable the GET with parentKeyString        
//        JComplex actual = template.getForObject(uri, JComplex.class);
//        assertNotNull("Complex", actual);
//        assertEquals("Complex organizationId", (Long) Long.parseLong(org.getId()), actual.getOrganizationId());
    }
    
    @Test
    public void testDeleteSample() {
        LOG.info("------------------- testDeleteSample() -----------------------");
        final String NAME = "mySampleName";
        JSample request = new JSample();
        request.setName(NAME);
        URI uri = template.postForLocation(BASE_URL + "sample/v10", request);
        
        template.delete(uri);
        
        try {
            JSample actual = template.getForObject(uri, JSample.class);
            fail("Expected NOT_FOUND");
        }
        catch (HttpClientErrorException expected) {
            assertEquals("Delete status", "404 Not Found", expected.getMessage());
        }
    }
    
    @Test
    public void testUpdateSample() throws InterruptedException {
        LOG.info("------------------- testUpdateSample() -----------------------");
        final String NAME = "mySampleName";
        JSample request = new JSample();
        request.setName("initialName");
        URI uri = template.postForLocation(BASE_URL + "sample/v10", request);
        Thread.sleep(1000);
        request = template.getForObject(uri, JSample.class);
        
        // update
        LOG.info("POST {}", uri);
        request.setName(NAME);
        template.postForLocation(uri, request);
        
        LOG.info("GET {}", uri);
        JSample actual = template.getForObject(uri, JSample.class);
        assertEquals("Updated name", NAME, actual.getName());
        assertTrue("UpdatedDate", actual.getCreatedDate() < actual.getUpdatedDate());
    }
    
}
