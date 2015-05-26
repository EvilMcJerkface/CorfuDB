package org.corfudb.runtime.collections;

import org.corfudb.runtime.CorfuDBRuntime;
import org.corfudb.runtime.smr.*;
import org.corfudb.runtime.stream.IStream;
import org.corfudb.runtime.stream.ITimestamp;
import org.corfudb.runtime.stream.SimpleStream;
import org.corfudb.runtime.view.*;
import org.junit.Before;
import org.junit.Test;
import sun.java2d.pipe.SpanShapeRenderer;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
/**
 * Created by mwei on 5/1/15.
 */
public class CDBSimpleMapTest {

    IStream s;
    ICorfuDBInstance instance;
    CDBSimpleMap<Integer, Integer> testMap;
    UUID streamID;
    CorfuDBRuntime cdr;

    @Before
    public void generateStream() throws Exception
    {
        cdr = new CorfuDBRuntime("memory");
        ConfigurationMaster cm = new ConfigurationMaster(cdr);
        cm.resetAll();
        instance = cdr.getLocalInstance();
        streamID = UUID.randomUUID();
        s = instance.openStream(streamID);
        testMap = new CDBSimpleMap<Integer, Integer>(s);
    }

    @Test
    public void mapIsPuttableGettable()
    {
        testMap.put(0, 10);
        testMap.put(10, 20);
        assertThat(testMap.get(0))
                .isEqualTo(10);
        assertThat(testMap.get(10))
                .isEqualTo(20);
    }


    @Test
    public void multipleMapsContainSameData() throws Exception
    {
        testMap.put(0, 10);
        testMap.put(10, 100);
        IStream s2 = cdr.openStream(streamID, SimpleStream.class);
        CDBSimpleMap<Integer,Integer> testMap2 = new CDBSimpleMap<Integer,Integer>(s2);
        assertThat(testMap2.get(0))
                .isEqualTo(10);
        assertThat(testMap2.get(10))
                .isEqualTo(100);
    }

    @Test
    public void ensureMutatorAccessorsWork() throws Exception
    {
        testMap.put(0, 10);
        assertThat(testMap.put(0, 100))
                .isEqualTo(10);
    }

    @Test
    public void DeferredTransactionalTest() throws Exception
    {
        DeferredTransaction tx = new DeferredTransaction(cdr);
        testMap.put(10, 100);
        final CDBSimpleMap<Integer,Integer> txMap = testMap.getTransactionalContext(tx);
        tx.setTransaction((ITransactionCommand) (opts) -> {
            Integer result = txMap.get(10);
            if (result == 100) {
                txMap.put(10, 1000);
                return true;
            }
            return false;
        });
        ITimestamp txStamp = tx.propose();
        testMap.getSMREngine().sync(txStamp);
        assertThat(testMap.get(10))
                .isEqualTo(1000);
    }

    @Test
    public void crossMapSwapTransactionalTest() throws Exception
    {
        DeferredTransaction tx = new DeferredTransaction(cdr);
        IStream s2 = cdr.openStream(UUID.randomUUID(), SimpleStream.class);
        CDBSimpleMap<Integer,Integer> testMap2 = new CDBSimpleMap<Integer,Integer>(s2);

        testMap.put(10, 100);
        testMap2.put(10, 1000);
        final CDBSimpleMap<Integer,Integer> txMap = testMap.getTransactionalContext(tx);
        final CDBSimpleMap<Integer,Integer> txMap2 = testMap2.getTransactionalContext(tx);

        tx.setTransaction((ITransactionCommand) (opts) -> {
            Integer old1 = txMap.get(10);
            Integer old2 = txMap2.put(10, old1);
            txMap.put(10, old2);
            return true;
        });

        ITimestamp txStamp = tx.propose();
        testMap.getSMREngine().sync(txStamp);
        testMap2.getSMREngine().sync(txStamp);
        assertThat(testMap.get(10))
                .isEqualTo(1000);
        assertThat(testMap2.get(10))
                .isEqualTo(100);
    }

    @Test
    public void mapOfMapsTest() throws Exception
    {
        DeferredTransaction tx = new DeferredTransaction(cdr);
        IStream s2 = cdr.openStream(UUID.randomUUID(), SimpleStream.class);
        CDBSimpleMap<Integer, CDBSimpleMap<Integer, Integer>> testMap2 =
                new CDBSimpleMap<Integer, CDBSimpleMap<Integer, Integer>>(s2);
        testMap2.put(10, testMap);
        testMap.put(100, 1000);
        assertThat(testMap2.get(10).get(100))
                .isEqualTo(1000);
    }

    @Test
    public void OpaqueDeferredTransactionalTest() throws Exception
    {
        OpaqueDeferredTransaction tx = new OpaqueDeferredTransaction(cdr);
        testMap.put(10, 100);
        final CDBSimpleMap<Integer, Integer> txMap = testMap.getTransactionalContext(tx);
        tx.setTransaction((ITransactionCommand) (opts) -> {
            Integer result = txMap.get(10);
            if (result == 100) {
                txMap.put(10, 1000);
                return true;
            }
            return false;
        });
        ITimestamp txStamp = tx.propose();
        testMap.getSMREngine().sync(txStamp);
        assertThat(testMap.get(10))
                .isEqualTo(1000);
        OpaqueDeferredTransaction txAbort = new OpaqueDeferredTransaction(cdr);
        final CDBSimpleMap<Integer, Integer> txMap2 = testMap.getTransactionalContext(tx);
        txAbort.setTransaction((ITransactionCommand) (opts) -> {
            Integer result = txMap2.get(10);
            assertThat(result)
                    .isEqualTo(1000);
            txMap2.put(10, 42);
            result = txMap2.get(10);
            assertThat(result)
                    .isEqualTo(42);
            return false;
        });
        ITimestamp abortStamp = txAbort.propose();
        testMap.getSMREngine().sync(abortStamp);
        assertThat(testMap.get(10))
                .isNotEqualTo(42)
                .isEqualTo(1000);
    }

    @Test
    public void TimeTravelSMRTest()
    {
        IStream s1 = instance.openStream(UUID.randomUUID());
        CDBSimpleMap<Integer, Integer> map = new CDBSimpleMap<Integer,Integer>(
                s1,
                TimeTravelSMREngine.class
        );

        map.put(10, 100);
        map.put(100, 1000);
        ITimestamp ts1 = map.getSMREngine().getLastProposal();
        map.put(100, 1234);
        ITimestamp ts2 = map.getSMREngine().getLastProposal();
        map.remove(100);
        ITimestamp ts3 = map.getSMREngine().getLastProposal();
        map.put(100, 5678);
        ITimestamp ts4 = map.getSMREngine().getLastProposal();

        assertThat(map.get(100))
                .isEqualTo(5678);
        assertThat(map.get(10))
                .isEqualTo(100);

        TimeTravelSMREngine t = (TimeTravelSMREngine) map.getSMREngine();
        t.travelAndLock(ts1);
        assertThat(map.get(100))
                .isEqualTo(1000);
        assertThat(map.get(10))
                .isEqualTo(100);

        t.travelAndLock(ts4);
        assertThat(map.get(100))
                .isEqualTo(5678);
        assertThat(map.get(10))
                .isEqualTo(100);

        t.travelAndLock(ts3);
        assertThat(map.get(100))
                .isEqualTo(null);
        assertThat(map.get(10))
                .isEqualTo(100);

        t.travelAndLock(ts2);
        assertThat(map.get(100))
                .isEqualTo(1234);
        assertThat(map.get(10))
                .isEqualTo(100);
    }
}