/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.balancing.HttpServiceBalancerStats.Status;
import com.proofpoint.stats.SparseCounterStat;
import com.proofpoint.stats.SparseTimeStat;
import com.proofpoint.testing.TestingTicker;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.Assertions.assertLessThan;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.fail;

public class TestHttpServiceBalancerImpl
{
    private HttpServiceBalancerImpl httpServiceBalancer;
    @Mock
    private HttpServiceBalancerStats httpServiceBalancerStats;
    @Mock
    private SparseTimeStat failureTimeStat;
    @Mock
    private SparseTimeStat successTimeStat;
    @Mock
    private SparseCounterStat counterStat;
    private TestingTicker testingTicker;

    @BeforeMethod
    protected void setUp()
    {
        initMocks(this);
        testingTicker = new TestingTicker();
        httpServiceBalancer = new HttpServiceBalancerImpl("type=[apple], pool=[pool]", httpServiceBalancerStats, new HttpServiceBalancerConfig().setConsecutiveFailures(5), testingTicker);
        when(httpServiceBalancerStats.requestTime(any(URI.class), eq(Status.FAILURE))).thenReturn(failureTimeStat);
        when(httpServiceBalancerStats.requestTime(any(URI.class), eq(Status.SUCCESS))).thenReturn(successTimeStat);
        when(httpServiceBalancerStats.failure(any(URI.class), eq("testing failure"))).thenReturn(counterStat);
    }

    @Test(expectedExceptions = ServiceUnavailableException.class)
    public void testNotStartedEmpty()
    {
        httpServiceBalancer.createAttempt();
    }

    @Test(expectedExceptions = ServiceUnavailableException.class)
    public void testStartedEmpty()
    {
        httpServiceBalancer.updateHttpUris(Set.of());

        httpServiceBalancer.createAttempt();
    }

    @Test
    public void testStartedWithServices()
    {
        Set<URI> expected = Set.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-a.example.com"));

        httpServiceBalancer.updateHttpUris(expected);

        Set<URI> uris = new HashSet<>();
        testingTicker.elapseTime(3000, TimeUnit.SECONDS);
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        testingTicker.elapseTime(5, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.elapseTime(5, TimeUnit.SECONDS);
        attempt.markBad("testing failure");
        testingTicker.elapseTime(5, TimeUnit.SECONDS);
        attempt = attempt.next();
        testingTicker.elapseTime(5, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.elapseTime(5, TimeUnit.SECONDS);
        attempt.markBad("testing failure");
        testingTicker.elapseTime(10, TimeUnit.SECONDS);
        attempt = attempt.next();
        testingTicker.elapseTime(10, TimeUnit.SECONDS);
        uris.add(attempt.getUri());
        testingTicker.elapseTime(10, TimeUnit.SECONDS);
        attempt.markGood();

        assertEquals(uris, expected);
        for (URI uri : expected) {
            verify(httpServiceBalancerStats).requestTime(uri, Status.FAILURE);
            verify(httpServiceBalancerStats).failure(uri, "testing failure");
        }
        verify(httpServiceBalancerStats).requestTime(any(URI.class), eq(Status.SUCCESS));
        verify(failureTimeStat, times(2)).add(TimeUnit.SECONDS.toNanos(10), TimeUnit.NANOSECONDS);
        verify(successTimeStat).add(TimeUnit.SECONDS.toNanos(20), TimeUnit.NANOSECONDS);
        verify(counterStat, times(2)).add(1);
        verifyNoMoreInteractions(httpServiceBalancerStats, failureTimeStat, successTimeStat, counterStat);
    }

    @Test
    public void testTakesUpdates()
    {
        URI firstUri = URI.create("http://apple-a.example.com");
        URI secondUri = URI.create("https://apple-a.example.com");
        when(httpServiceBalancerStats.failure(any(URI.class), eq("testing failure"), eq("testing category"))).thenReturn(counterStat);

        httpServiceBalancer.updateHttpUris(Set.of(firstUri));

        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        assertEquals(attempt.getUri(), firstUri);
        attempt.markBad("testing failure", "testing category");

        verify(httpServiceBalancerStats).requestTime(firstUri, Status.FAILURE);
        verify(httpServiceBalancerStats).failure(firstUri, "testing failure", "testing category");

        httpServiceBalancer.updateHttpUris(Set.of(firstUri, secondUri));
        attempt = attempt.next();
        assertEquals(attempt.getUri(), secondUri);
        attempt.markGood();
    }

    @Test
    public void testReuseUri()
    {
        Set<URI> expected = Set.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-a.example.com"));

        httpServiceBalancer.updateHttpUris(expected);

        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        attempt.markBad("testing failure");
        attempt = attempt.next();
        attempt.markBad("testing failure");

        Set<URI> uris = new HashSet<>();
        attempt = attempt.next();
        uris.add(attempt.getUri());
        attempt.markBad("testing failure");
        attempt = attempt.next();
        uris.add(attempt.getUri());
        attempt.markGood();

        assertEquals(uris, expected);
    }

    @Test
    public void testMinimizeConcurrentAttempts()
    {
        Set<URI> expected = Set.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-a.example.com"));

        httpServiceBalancer.updateHttpUris(expected);

        for (int i = 0; i < 10; ++i) {
            HttpServiceAttempt attempt1 = httpServiceBalancer.createAttempt();
            HttpServiceAttempt attempt2 = httpServiceBalancer.createAttempt();

            assertNotEquals(attempt2.getUri(), attempt1.getUri(), "concurrent attempt");
            attempt2.markBad("testing failure");
            attempt2 = attempt2.next();
            assertEquals(attempt2.getUri(), attempt1.getUri());
            attempt2.markBad("testing failure");
            attempt2 = attempt2.next();
            assertNotEquals(attempt2.getUri(), attempt1.getUri(), "concurrent attempt");
            attempt1.markGood();
            attempt1 = httpServiceBalancer.createAttempt();
            assertNotEquals(attempt1.getUri(), attempt2.getUri(), "concurrent attempt");

            HttpServiceAttempt attempt3 = httpServiceBalancer.createAttempt();
            HttpServiceAttempt attempt4 = httpServiceBalancer.createAttempt();

            assertNotEquals(attempt4.getUri(), attempt3.getUri(), "concurrent attempt");
            attempt4.markBad("testing failure");
            attempt4 = attempt4.next();
            assertEquals(attempt4.getUri(), attempt3.getUri());
            attempt4.markBad("testing failure");
            attempt4 = attempt4.next();
            assertNotEquals(attempt4.getUri(), attempt3.getUri(), "concurrent attempt");
            attempt3.markGood();
            attempt3 = httpServiceBalancer.createAttempt();
            assertNotEquals(attempt3.getUri(), attempt4.getUri(), "concurrent attempt");

            attempt1.markGood();
            attempt2.markGood();
            attempt3.markGood();
            attempt4.markGood();
        }
    }

    @Test
    public void testWeighted()
    {
        URI uriLowWeight = URI.create("http://apple-a.example.com");
        URI uriHighWeight = URI.create("https://apple-a.example.com");
        httpServiceBalancer.updateHttpUris(List.of(uriLowWeight, uriHighWeight, uriHighWeight, uriHighWeight));

        consumeUri(uriHighWeight);
        assertThat(frequencyOfUri(uriLowWeight)).isBetween(25.0, 40.0);

        consumeUri(uriHighWeight);
        assertThat(frequencyOfUri(uriLowWeight)).isBetween(45.0, 55.0);

        consumeUri(uriHighWeight);
        assertThat(frequencyOfUri(uriLowWeight)).isEqualTo(100.0);

        consumeUri(uriLowWeight);
        assertThat(frequencyOfUri(uriLowWeight)).isBetween(20.0, 30.0);
    }

    private void consumeUri(URI uri)
    {
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        for (int i = 0; i < 10_000; i++) {
            if (attempt.getUri().equals(uri)) {
                return;
            }
            attempt.markBad("testing failure");
            attempt = attempt.next();
        }
        fail(String.format("expected to eventually get uri %s", uri));
    }

    private double frequencyOfUri(URI uri)
    {
        double foundCount = 0;
        for (int i = 0; i < 10_000; i++) {
            HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
            if (uri.equals(attempt.getUri())) {
                ++foundCount;
            }
            attempt.markGood();
        }
        return foundCount / 100.0;
    }

    @Test
    public void testPersistentlyFailingInstanceRemoved()
    {
        URI goodUri = URI.create("http://good.example.com");
        Set<URI> expected = Set.of(goodUri, URI.create("https://bad.example.com"));
        SparseTimeStat removalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("https://bad.example.com"))).thenReturn(removalStat);

        httpServiceBalancer.updateHttpUris(expected);

        int goodFailed = 0;
        int badFailed = 0;
        for (int i = 0; i < 10_000; i++) {
            HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
            if (attempt.getUri().equals(goodUri)) {
                if (goodFailed == 4) {
                    goodFailed = 0;
                    attempt.markGood();
                }
                else {
                    goodFailed++;
                    attempt.markBad("testing failure");
                }
            }
            else {
                assertLessThan(badFailed, 5);
                badFailed++;
                attempt.markBad("testing failure");
            }
        }
        assertEquals(badFailed, 5);

        verify(removalStat).add(any());
    }

    @Test
    public void testRemovedInstanceProbeSucceeds()
    {
        URI goodUri = URI.create("http://good.example.com");
        URI badUri = URI.create("https://bad.example.com");
        Set<URI> expected = Set.of(goodUri, badUri);
        SparseTimeStat removalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("https://bad.example.com"))).thenReturn(removalStat);
        SparseCounterStat probeStat = mock(SparseCounterStat.class);
        when(httpServiceBalancerStats.probe(URI.create("https://bad.example.com"))).thenReturn(probeStat);
        SparseCounterStat revivalStat = mock(SparseCounterStat.class);
        when(httpServiceBalancerStats.revival(URI.create("https://bad.example.com"))).thenReturn(revivalStat);

        httpServiceBalancer.updateHttpUris(expected);

        // Increase concurrency on goodUri to 1
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        while (attempt.getUri().equals(badUri)) {
            attempt.markGood();
            attempt = httpServiceBalancer.createAttempt();
        }

        // Mark badUri as down
        for (int i = 0; i < 5; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markBad("testing failure");
        }
        verify(removalStat).add(any());

        testingTicker.elapseTime(10, TimeUnit.SECONDS);

        attempt = httpServiceBalancer.createAttempt();
        assertEquals(attempt.getUri(), badUri);
        verify(probeStat).add(1);
        verifyNoMoreInteractions(revivalStat);
        attempt.markGood();
        verify(revivalStat).add(1);

        for (int i = 0; i < 10_000; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markGood();
        }
        verifyNoMoreInteractions(removalStat);
        verifyNoMoreInteractions(probeStat);
        verifyNoMoreInteractions(revivalStat);
    }

    @Test
    public void testRemovedInstanceProbeFails()
    {
        URI goodUri = URI.create("http://good.example.com");
        URI badUri = URI.create("https://bad.example.com");
        Set<URI> expected = Set.of(goodUri, badUri);
        SparseTimeStat removalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("https://bad.example.com"))).thenReturn(removalStat);
        SparseCounterStat probeStat = mock(SparseCounterStat.class);
        when(httpServiceBalancerStats.probe(URI.create("https://bad.example.com"))).thenReturn(probeStat);
        httpServiceBalancer.updateHttpUris(expected);

        // Increase concurrency on goodUri to 1
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        while (attempt.getUri().equals(badUri)) {
            attempt.markGood();
            attempt = httpServiceBalancer.createAttempt();
        }

        // Mark badUri as down
        for (int i = 0; i < 5; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markBad("testing failure");
        }
        verify(removalStat).add(any());

        testingTicker.elapseTime(10, TimeUnit.SECONDS);

        attempt = httpServiceBalancer.createAttempt();
        assertEquals(attempt.getUri(), badUri);
        verify(probeStat).add(1);
        attempt.markBad("testing failure");
        verify(removalStat, times(2)).add(any());

        for (int i = 0; i < 10_000; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), goodUri);
            attempt.markGood();
        }
        verifyNoMoreInteractions(removalStat);
        verifyNoMoreInteractions(probeStat);
    }

    @Test
    public void testRemovedInstanceSucceeds()
    {
        URI goodUri = URI.create("http://good.example.com");
        URI badUri = URI.create("https://bad.example.com");
        Set<URI> expected = Set.of(goodUri, badUri);
        SparseTimeStat removalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("https://bad.example.com"))).thenReturn(removalStat);
        SparseCounterStat revivalStat = mock(SparseCounterStat.class);
        when(httpServiceBalancerStats.revival(URI.create("https://bad.example.com"))).thenReturn(revivalStat);

        httpServiceBalancer.updateHttpUris(expected);

        // Increase concurrency on goodUri to 1
        HttpServiceAttempt attempt = httpServiceBalancer.createAttempt();
        while (attempt.getUri().equals(badUri)) {
            attempt.markGood();
            attempt = httpServiceBalancer.createAttempt();
        }

        HttpServiceAttempt oldAttemptOnBad = httpServiceBalancer.createAttempt();
        assertEquals(oldAttemptOnBad.getUri(), badUri);

        // Increase concurrency on goodUri to 2
        attempt = httpServiceBalancer.createAttempt();
        while (attempt.getUri().equals(badUri)) {
            attempt.markGood();
            attempt = httpServiceBalancer.createAttempt();
        }

        // Mark badUri as down
        for (int i = 0; i < 5; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markBad("testing failure");
        }
        verify(removalStat).add(any());

        attempt = httpServiceBalancer.createAttempt();
        assertEquals(attempt.getUri(), goodUri);
        attempt.markGood();

        oldAttemptOnBad.markGood();
        verify(revivalStat).add(1);

        for (int i = 0; i < 10_000; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markGood();
        }
        verifyNoMoreInteractions(removalStat);
        verifyNoMoreInteractions(revivalStat);
    }

    @Test
    public void testMinimizeConcurrentAvoidsRemovedInstances()
    {
        URI goodUri1 = URI.create("http://good1.example.com");
        URI goodUri2 = URI.create("http://good2.example.com");
        URI badUri = URI.create("https://bad.example.com");
        Set<URI> expected = Set.of(goodUri1, goodUri2, badUri);
        SparseTimeStat badRemovalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("https://bad.example.com"))).thenReturn(badRemovalStat);
        SparseTimeStat good1RemovalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("http://good1.example.com"))).thenReturn(good1RemovalStat);
        SparseTimeStat good2RemovalStat = mock(SparseTimeStat.class);
        when(httpServiceBalancerStats.removal(URI.create("http://good2.example.com"))).thenReturn(good2RemovalStat);

        httpServiceBalancer.updateHttpUris(expected);
        // Increase concurrency on goodUris to 1
        HttpServiceAttempt attempt;
        for (int i = 0; i < 2; i++) {
            attempt = httpServiceBalancer.createAttempt();
            while (attempt.getUri().equals(badUri)) {
                attempt.markGood();
                attempt = httpServiceBalancer.createAttempt();
            }
        }

        // Mark badUri as down
        for (int i = 0; i < 5; i++) {
            attempt = httpServiceBalancer.createAttempt();
            assertEquals(attempt.getUri(), badUri);
            attempt.markBad("testing failure");
        }
        verify(badRemovalStat).add(any());

        HttpServiceAttempt attempt1 = httpServiceBalancer.createAttempt();
        HttpServiceAttempt attempt2 = httpServiceBalancer.createAttempt();
        for (int i = 0; i < 5; i++) {
            assertNotEquals(attempt1.getUri(), badUri);
            assertNotEquals(attempt2.getUri(), badUri);
            assertNotEquals(attempt2.getUri(), attempt1.getUri(), "concurrent attempt");
            attempt1.markBad("testing failure");
            attempt1 = attempt1.next();
            attempt2.markBad("testing failure");
            attempt2 = attempt2.next();
        }
        verify(good1RemovalStat).add(any());
        verify(good2RemovalStat).add(any());

        for (int i = 0; i < 5; i++) {
            // All are marked dead, so badUri can be in the mix.
            // The balancer can repeat URIs in this case. assertNotEquals(attempt2.getUri(), attempt1.getUri(), "concurrent attempt on " + attempt1.getUri());
            // This test is asserting that we still give out attempts when all URIs are bad.
            attempt1.markBad("testing failure");
            attempt1 = attempt1.next();
            attempt2.markBad("testing failure");
            attempt2 = attempt2.next();
        }
        verifyNoMoreInteractions(badRemovalStat);
        verifyNoMoreInteractions(good1RemovalStat);
        verifyNoMoreInteractions(good2RemovalStat);
    }
}
