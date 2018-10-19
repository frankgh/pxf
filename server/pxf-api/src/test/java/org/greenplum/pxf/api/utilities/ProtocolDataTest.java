package org.greenplum.pxf.api.utilities;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */


import org.greenplum.pxf.api.OutputFormat;
import org.greenplum.pxf.api.utilities.ProfileConfException;

import static org.greenplum.pxf.api.utilities.ProfileConfException.MessageFormat.NO_PROFILE_DEF;

import org.greenplum.pxf.api.utilities.ProfilesConf;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ ProfilesConf.class })
public class ProtocolDataTest {
    Map<String, String> parameters;

    /*
     * setUp function called before each test
     */
    @Before
    public void setUp() {
        parameters = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

        parameters.put("X-GP-ALIGNMENT", "all");
        parameters.put("X-GP-SEGMENT-ID", "-44");
        parameters.put("X-GP-SEGMENT-COUNT", "2");
        parameters.put("X-GP-HAS-FILTER", "0");
        parameters.put("X-GP-FORMAT", "TEXT");
        parameters.put("X-GP-URL-HOST", "my://bags");
        parameters.put("X-GP-URL-PORT", "-8020");
        parameters.put("X-GP-ATTRS", "-1");
        parameters.put("X-GP-OPTIONS-ACCESSOR", "are");
        parameters.put("X-GP-OPTIONS-RESOLVER", "packed");
        parameters.put("X-GP-DATA-DIR", "i'm/ready/to/go");
        parameters.put("X-GP-FRAGMENT-METADATA", "U29tZXRoaW5nIGluIHRoZSB3YXk=");
        parameters.put("X-GP-OPTIONS-I'M-STANDING-HERE", "outside-your-door");
        parameters.put("X-GP-USER", "alex");
    }

    @Test
    public void protocolDataCreated() throws Exception {
        ProtocolData protocolData = new ProtocolData(parameters);

        // TODO: expected and actual values are reversed here
        assertEquals(System.getProperty("greenplum.alignment"), "all");
        assertEquals(protocolData.getTotalSegments(), 2);
        assertEquals(protocolData.getSegmentId(), -44);
        assertEquals(protocolData.outputFormat(), OutputFormat.TEXT);
        assertEquals(protocolData.serverName(), "my://bags");
        assertEquals(protocolData.serverPort(), -8020);
        assertFalse(protocolData.hasFilter());
        assertNull(protocolData.getFilterString());
        assertEquals(protocolData.getColumns(), 0);
        assertEquals(protocolData.getDataFragment(), -1);
        assertNull(protocolData.getRecordkeyColumn());
        assertEquals(protocolData.getAccessor(), "are");
        assertEquals(protocolData.getResolver(), "packed");
        assertEquals(protocolData.getDataSource(), "i'm/ready/to/go");
        assertEquals(protocolData.getUserProperty("i'm-standing-here"), "outside-your-door");
        assertEquals(protocolData.getUser(), "alex");
        assertEquals(protocolData.getParametersMap(), parameters);
        assertNull(protocolData.getLogin());
        assertNull(protocolData.getSecret());
    }

    @Test
    public void profileWithDuplicateProperty() throws Exception {
        PowerMockito.mockStatic(ProfilesConf.class);

        Map<String, String> mockedProfiles = new HashMap<>();
        mockedProfiles.put("wHEn you trY yOUR bESt", "but you dont succeed");
        mockedProfiles.put("when YOU get WHAT you WANT",
                "but not what you need");
        mockedProfiles.put("when you feel so tired", "but you cant sleep");

        when(ProfilesConf.getProfilePluginsMap("a profile")).thenReturn(
                mockedProfiles);

        parameters.put("x-gp-options-profile", "a profile");
        parameters.put("when you try your best", "and you do succeed");
        parameters.put("WHEN you GET what YOU want", "and what you need");

        try {
            new ProtocolData(parameters);
            fail("Duplicate property should throw IllegalArgumentException");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "Profile 'a profile' already defines: [when YOU get WHAT you WANT, wHEn you trY yOUR bESt]",
                    iae.getMessage());
        }
    }

    @Test
    public void definedProfile() throws Exception {
        parameters.put("X-GP-OPTIONS-PROFILE", "HIVE");
        parameters.remove("X-GP-OPTIONS-ACCESSOR");
        parameters.remove("X-GP-OPTIONS-RESOLVER");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.getFragmenter(), "org.greenplum.pxf.plugins.hive.HiveDataFragmenter");
        assertEquals(protocolData.getAccessor(), "org.greenplum.pxf.plugins.hive.HiveAccessor");
        assertEquals(protocolData.getResolver(), "org.greenplum.pxf.plugins.hive.HiveResolver");
    }

    @Test
    public void undefinedProfile() throws Exception {
        parameters.put("X-GP-OPTIONS-PROFILE", "THIS_PROFILE_NEVER_EXISTED!");
        try {
            new ProtocolData(parameters);
            fail("Undefined profile should throw ProfileConfException");
        } catch (ProfileConfException pce) {
            assertEquals(pce.getMsgFormat(), NO_PROFILE_DEF);
        }
    }

    @Test
    public void profileStartingWithS3SetsTheUrlScheme() throws Exception {
        parameters.put("X-GP-OPTIONS-PROFILE", "S3Text");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals("s3://i'm/ready/to/go", protocolData.dataSource);
    }

    @Test
    public void profileStartingWithADLSetsTheUrlScheme() throws Exception {
        parameters.put("X-GP-OPTIONS-PROFILE", "ADLParquet");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals("adl://i'm/ready/to/go", protocolData.dataSource);
    }

    @Test
    public void threadSafeTrue() throws Exception {
        parameters.put("X-GP-OPTIONS-THREAD-SAFE", "TRUE");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.isThreadSafe(), true);

        parameters.put("X-GP-OPTIONS-THREAD-SAFE", "true");
        protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.isThreadSafe(), true);
    }

    @Test
    public void threadSafeFalse() throws Exception {
        parameters.put("X-GP-OPTIONS-THREAD-SAFE", "False");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.isThreadSafe(), false);

        parameters.put("X-GP-OPTIONS-THREAD-SAFE", "falSE");
        protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.isThreadSafe(), false);
    }

    @Test
    public void threadSafeMaybe() throws Exception {
        parameters.put("X-GP-OPTIONS-THREAD-SAFE", "maybe");
        try {
            new ProtocolData(parameters);
            fail("illegal THREAD-SAFE value should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    "Illegal boolean value 'maybe'. Usage: [TRUE|FALSE]");
        }
    }

    @Test
    public void threadSafeDefault() throws Exception {
        parameters.remove("X-GP-OPTIONS-THREAD-SAFE");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertEquals(protocolData.isThreadSafe(), true);
    }

    @Test
    public void getFragmentMetadata() throws Exception {
        ProtocolData protocolData = new ProtocolData(parameters);
        byte[] location = protocolData.getFragmentMetadata();
        assertEquals(new String(location), "Something in the way");
    }

    @Test
    public void getFragmentMetadataNull() throws Exception {
        parameters.remove("X-GP-FRAGMENT-METADATA");
        ProtocolData ProtocolData = new ProtocolData(parameters);
        assertNull(ProtocolData.getFragmentMetadata());
    }

    @Test
    public void getFragmentMetadataNotBase64() throws Exception {
        String badValue = "so b@d";
        parameters.put("X-GP-FRAGMENT-METADATA", badValue);
        try {
            new ProtocolData(parameters);
            fail("should fail with bad fragment metadata");
        } catch (Exception e) {
            assertEquals(e.getMessage(),
                    "Fragment metadata information must be Base64 encoded."
                            + "(Bad value: " + badValue + ")");
        }
    }

    @Test
    public void nullUserThrowsException() throws Exception {
        parameters.remove("X-GP-USER");
        try {
            new ProtocolData(parameters);
            fail("null X-GP-USER should throw exception");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(),
                    "Internal server error. Property \"USER\" has no value in current request");
        }
    }

    @Test
    public void filterUtf8() throws Exception {
        parameters.remove("X-GP-HAS-FILTER");
        parameters.put("X-GP-HAS-FILTER", "1");
        parameters.put("X-GP-FILTER", "UTF8_計算機用語_00000000");
        ProtocolData protocolData = new ProtocolData(parameters);
        assertTrue(protocolData.hasFilter());
        assertEquals("UTF8_計算機用語_00000000", protocolData.getFilterString());
    }

    @Test
    public void noStatsParams() {
        ProtocolData protData = new ProtocolData(parameters);

        assertEquals(0, protData.getStatsMaxFragments());
        assertEquals(0, protData.getStatsSampleRatio(), 0.1);
    }

    @Test
    public void statsParams() {
        parameters.put("X-GP-OPTIONS-STATS-MAX-FRAGMENTS", "10101");
        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "0.039");

        ProtocolData protData = new ProtocolData(parameters);

        assertEquals(10101, protData.getStatsMaxFragments());
        assertEquals(0.039, protData.getStatsSampleRatio(), 0.01);
    }

    @Test
    public void statsMissingParams() {
        parameters.put("X-GP-OPTIONS-STATS-MAX-FRAGMENTS", "13");
        try {
            new ProtocolData(parameters);
            fail("missing X-GP-OPTIONS-STATS-SAMPLE-RATIO parameter");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    e.getMessage(),
                    "Missing parameter: STATS-SAMPLE-RATIO and STATS-MAX-FRAGMENTS must be set together");
        }

        parameters.remove("X-GP-OPTIONS-STATS-MAX-FRAGMENTS");
        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "1");
        try {
            new ProtocolData(parameters);
            fail("missing X-GP-OPTIONS-STATS-MAX-FRAGMENTS parameter");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    e.getMessage(),
                    "Missing parameter: STATS-SAMPLE-RATIO and STATS-MAX-FRAGMENTS must be set together");
        }
    }

    @Test
    public void statsSampleRatioNegative() {
        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "101");

        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-SAMPLE-RATIO value");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    e.getMessage(),
                    "Wrong value '101.0'. "
                            + "STATS-SAMPLE-RATIO must be a value between 0.0001 and 1.0");
        }

        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "0");
        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-SAMPLE-RATIO value");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    e.getMessage(),
                    "Wrong value '0.0'. "
                            + "STATS-SAMPLE-RATIO must be a value between 0.0001 and 1.0");
        }

        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "0.00005");
        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-SAMPLE-RATIO value");
        } catch (IllegalArgumentException e) {
            assertEquals(
                    e.getMessage(),
                    "Wrong value '5.0E-5'. "
                            + "STATS-SAMPLE-RATIO must be a value between 0.0001 and 1.0");
        }

        parameters.put("X-GP-OPTIONS-STATS-SAMPLE-RATIO", "a");
        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-SAMPLE-RATIO value");
        } catch (NumberFormatException e) {
            assertEquals(e.getMessage(), "For input string: \"a\"");
        }
    }

    @Test
    public void statsMaxFragmentsNegative() {
        parameters.put("X-GP-OPTIONS-STATS-MAX-FRAGMENTS", "10.101");

        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-MAX-FRAGMENTS value");
        } catch (NumberFormatException e) {
            assertEquals(e.getMessage(), "For input string: \"10.101\"");
        }

        parameters.put("X-GP-OPTIONS-STATS-MAX-FRAGMENTS", "0");

        try {
            new ProtocolData(parameters);
            fail("wrong X-GP-OPTIONS-STATS-MAX-FRAGMENTS value");
        } catch (IllegalArgumentException e) {
            assertEquals(e.getMessage(), "Wrong value '0'. "
                    + "STATS-MAX-FRAGMENTS must be a positive integer");
        }
    }

    @Test
    public void typeMods() {

        parameters.put("X-GP-ATTRS", "2");
        parameters.put("X-GP-ATTR-NAME0", "vc1");
        parameters.put("X-GP-ATTR-TYPECODE0", "1043");
        parameters.put("X-GP-ATTR-TYPENAME0", "varchar");
        parameters.put("X-GP-ATTR-TYPEMOD0-COUNT", "1");
        parameters.put("X-GP-ATTR-TYPEMOD0-0", "5");

        parameters.put("X-GP-ATTR-NAME1", "dec1");
        parameters.put("X-GP-ATTR-TYPECODE1", "1700");
        parameters.put("X-GP-ATTR-TYPENAME1", "numeric");
        parameters.put("X-GP-ATTR-TYPEMOD1-COUNT", "2");
        parameters.put("X-GP-ATTR-TYPEMOD1-0", "10");
        parameters.put("X-GP-ATTR-TYPEMOD1-1", "2");

        ProtocolData protocolData = new ProtocolData(parameters);

        assertArrayEquals(protocolData.getColumn(0).columnTypeModifiers(), new Integer[]{5});
        assertArrayEquals(protocolData.getColumn(1).columnTypeModifiers(), new Integer[]{10, 2});
    }

    @Test
    public void typeModsNegative() {

        parameters.put("X-GP-ATTRS", "1");
        parameters.put("X-GP-ATTR-NAME0", "vc1");
        parameters.put("X-GP-ATTR-TYPECODE0", "1043");
        parameters.put("X-GP-ATTR-TYPENAME0", "varchar");
        parameters.put("X-GP-ATTR-TYPEMOD0-COUNT", "X");
        parameters.put("X-GP-ATTR-TYPEMOD0-0", "Y");


        try {
            ProtocolData protocolData = new ProtocolData(parameters);
            fail("should throw IllegalArgumentException when bad value received for X-GP-ATTR-TYPEMOD0-COUNT");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "ATTR-TYPEMOD0-COUNT must be a positive integer",
                    iae.getMessage());
        }

        parameters.put("X-GP-ATTR-TYPEMOD0-COUNT", "-1");

        try {
            ProtocolData protocolData = new ProtocolData(parameters);
            fail("should throw IllegalArgumentException when negative value received for X-GP-ATTR-TYPEMOD0-COUNT");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "ATTR-TYPEMOD0-COUNT cann't be negative",
                    iae.getMessage());
        }

        parameters.put("X-GP-ATTR-TYPEMOD0-COUNT", "1");

        try {
            ProtocolData protocolData = new ProtocolData(parameters);
            fail("should throw IllegalArgumentException when bad value received for X-GP-ATTR-TYPEMOD0-0");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "ATTR-TYPEMOD0-0 must be a positive integer",
                    iae.getMessage());
        }

        parameters.put("X-GP-ATTR-TYPEMOD0-COUNT", "2");
        parameters.put("X-GP-ATTR-TYPEMOD0-0", "42");

        try {
            ProtocolData protocolData = new ProtocolData(parameters);
            fail("should throw IllegalArgumentException number of actual type modifiers is less than X-GP-ATTR-TYPEMODX-COUNT");
        } catch (IllegalArgumentException iae) {
            assertEquals(
                    "Internal server error. Property \"ATTR-TYPEMOD0-1\" has no value in current request",
                    iae.getMessage());
        }
    }

    /*
     * tearDown function called after each test
     */
    @After
    public void tearDown() {
        // Cleanup the system property ProtocolData sets
        System.clearProperty("greenplum.alignment");
    }
}
