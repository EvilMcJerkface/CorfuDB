/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.corfudb.runtime;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CyclicBarrier;

import gnu.getopt.Getopt;
import org.corfudb.runtime.collections.CorfuDBMap;
import org.corfudb.sharedlog.ClientLib;
import org.corfudb.sharedlog.CorfuException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import org.corfudb.runtime.collections.CorfuDBCounter;
import org.corfudb.runtime.collections.CorfuDBCoarseList;
import org.corfudb.runtime.collections.CorfuDBList;

/**
 * Tester code for the CorfuDB runtime stack
 *
 *
 */

public class CorfuDBTester
{

    static Logger dbglog = LoggerFactory.getLogger(CorfuDBTester.class);

    static void print_usage()
    {
        System.out.println("usage: java CorfuDBTester");
        System.out.println("\t-m masternode");
        System.out.println("\t[-a testtype] (0==TXTest|1==LinMapTest|2==StreamTest|3==MultiClientTXTest|4==LinCounterTest)");
        System.out.println("\t[-t number of threads]");
        System.out.println("\t[-n number of ops]");
        System.out.println("\t[-k number of keys used in list tests]");
        System.out.println("\t[-l number of lists used in list tests]");
        System.out.println("\t[-p rpcport]");

//        if(dbglog instanceof SimpleLogger)
//            System.out.println("using SimpleLogger: run with -Dorg.slf4j.simpleLogger.defaultLogLevel=debug to " +
//                    "enable debug printouts");
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        final int TXTEST=0;
        final int LINTEST=1;
        final int STREAMTEST=2;
        final int MULTICLIENTTXTEST=3;
        final int LINCTRTEST=4;
        final int TXLISTCOARSE=5;
        final int TXLISTFINE=6;

        int numclients = 1;
        int expernum = 1; //used by the barrier code

        int c;
        String strArg;
        int numthreads = 1;
        int numops = 1000;
        int numkeys = 100;
        int numlists = 2;
        int testnum = 0;
        int rpcport = 9090;
        String masternode = null;
        if(args.length==0)
        {
            print_usage();
            return;
        }

        Getopt g = new Getopt("CorfuDBTester", args, "a:m:t:n:k:l:");
        while ((c = g.getopt()) != -1)
        {
            switch(c)
            {
                case 'a':
                    strArg = g.getOptarg();
                    System.out.println("testtype = "+ strArg);
                    testnum = Integer.parseInt(strArg);
                    break;
                case 'm':
                    masternode = g.getOptarg();
                    masternode = masternode.trim();
                    System.out.println("master = " + masternode);
                    break;
                case 't':
                    strArg = g.getOptarg();
                    System.out.println("numthreads = "+ strArg);
                    numthreads = Integer.parseInt(strArg);
                    break;
                case 'n':
                    strArg = g.getOptarg();
                    System.out.println("numops = "+ strArg);
                    numops = Integer.parseInt(strArg);
                    break;
                case 'k':
                    strArg = g.getOptarg();
                    System.out.println("numkeys = "+ strArg);
                    numkeys = Integer.parseInt(strArg);
                    break;
                case 'l':
                    strArg = g.getOptarg();
                    System.out.println("numlists = "+ strArg);
                    numlists = Integer.parseInt(strArg);
                    break;
                case 'p':
                    strArg = g.getOptarg();
                    System.out.println("rpcport = "+ strArg);
                    rpcport = Integer.parseInt(strArg);
                    break;
                default:
                    System.out.print("getopt() returned " + c + "\n");
            }
        }

        if(masternode == null)
            throw new Exception("must provide master http address using -m flag");
        if(numthreads < 1)
            throw new Exception("need at least one thread!");
        if(numops < 1)
            throw new Exception("need at least one op!");


        String rpchostname;

        try
        {
            rpchostname = InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e)
        {
            throw new RuntimeException(e);
        }

        if(testnum==MULTICLIENTTXTEST)
        {
            if(args.length<4)
            {
                print_usage();
                return;
            }
            numclients = Integer.parseInt(args[2]);
            expernum = Integer.parseInt(args[3]);
        }



        ClientLib crf;

        try
        {
            crf = new ClientLib(masternode);
        }
        catch (CorfuException e)
        {
            throw e;
        }


        Thread[] threads = new Thread[numthreads];

        StreamFactory sf = new StreamFactoryImpl(new CorfuLogAddressSpace(crf), new CorfuStreamingSequencer(crf));

        long starttime = System.currentTimeMillis();

        if(testnum==MULTICLIENTTXTEST)
        {
            TXRuntime TR = new TXRuntime(sf, DirectoryService.getUniqueID(sf), rpchostname, rpcport);
            DirectoryService DS = new DirectoryService(TR);
            CorfuDBCounter barrier = new CorfuDBCounter(TR, DS.nameToStreamID("barrier" + expernum));
            barrier.increment();
            while(barrier.read() < numclients) ;
            dbglog.debug("Barrier reached; starting test...");
            testnum = TXTEST;
        }

        if(testnum==LINTEST)
        {
            SimpleRuntime TR = new SimpleRuntime(sf, DirectoryService.getUniqueID(sf), rpchostname, rpcport);
            CorfuDBMap<Integer, Integer> cob1 = new CorfuDBMap<Integer, Integer>(TR, DirectoryService.getUniqueID(sf));
            for (int i = 0; i < numthreads; i++)
            {
                //linearizable tester
                threads[i] = new Thread(new TesterThread(cob1));
                threads[i].start();
            }
            for(int i=0;i<numthreads;i++)
                threads[i].join();
            System.out.println("Test succeeded!");
        }
        if(testnum==LINCTRTEST)
        {
            SimpleRuntime TR = new SimpleRuntime(sf, DirectoryService.getUniqueID(sf), rpchostname, rpcport);
            CorfuDBCounter ctr1 = new CorfuDBCounter(TR, DirectoryService.getUniqueID(sf));
            for (int i = 0; i < numthreads; i++)
            {
                //linearizable tester
                threads[i] = new Thread(new TesterThread(ctr1));
                threads[i].start();
            }
            for(int i=0;i<numthreads;i++)
                threads[i].join();
            System.out.println("Test succeeded!");
        }
        else if(testnum==TXTEST)
        {
            TXRuntime TR = new TXRuntime(sf, DirectoryService.getUniqueID(sf), rpchostname, rpcport);

            DirectoryService DS = new DirectoryService(TR);
            CorfuDBMap<Integer, Integer> cob1 = new CorfuDBMap(TR, DS.nameToStreamID("testmap1"));
            CorfuDBMap<Integer, Integer> cob2 = new CorfuDBMap(TR, DS.nameToStreamID("testmap2"));


            for (int i = 0; i < numthreads; i++)
            {
                //transactional tester
                threads[i] = new Thread(new TXTesterThread(cob1, cob2, TR));
                threads[i].start();
            }
            for(int i=0;i<numthreads;i++)
                threads[i].join();
            System.out.println("Test done! Checking consistency...");
            TXTesterThread tx = new TXTesterThread(cob1, cob2, TR);
            if(tx.check_consistency())
                System.out.println("Consistency check passed --- test successful!");
            else
                System.out.println("Consistency check failed!");
            System.out.println(TR);
        }
        else if(testnum==STREAMTEST)
        {
            Stream sb = sf.newStream(1234);

            //trim the stream to get rid of entries from previous tests
            //sb.prefixTrim(sb.checkTail()); //todo: turning off, trim not yet implemented at log level
            for(int i=0;i<numthreads;i++)
            {
                threads[i] = new Thread(new StreamTester(sb, numops));
                threads[i].start();
            }
            for(int i=0;i<numthreads;i++)
                threads[i].join();
        }
        else if(testnum==TXLISTCOARSE)
        {
            CyclicBarrier startbarrier = new CyclicBarrier(numthreads);
            CyclicBarrier stopbarrier = new CyclicBarrier(numthreads);
            TXRuntime TR = new TXRuntime(sf, DirectoryService.getUniqueID(sf), rpchostname, rpcport);
            ArrayList<CorfuDBList<Integer>> lists = new ArrayList<CorfuDBList<Integer>>();
            NonRandomIntProvider generator = new NonRandomIntProvider();

            for(int i=0; i<numlists; i++) {
                lists.add(new CorfuDBCoarseList<Integer>(TR, DirectoryService.getUniqueID(sf)));
            }

            for (int i = 0; i < numthreads; i++)
            {
                TXListTester<Integer> txl = new TXListTester<Integer>(
                        i, startbarrier, stopbarrier, TR, lists, numops, numkeys, generator);
                threads[i] = new Thread(txl);
                threads[i].start();
            }
            for(int i=0;i<numthreads;i++)
                threads[i].join();

            System.out.println("Test done! Checking consistency...");
            TXListChecker txc = new TXListChecker(TR, lists, numops, numkeys);
            if(txc.isConsistent())
                System.out.println("List consistency check passed --- test successful!");
            else
                System.out.println("List consistency check failed!");
            System.out.println(TR);
        }

        System.out.println("Test done in " + (System.currentTimeMillis()-starttime));

        System.exit(0);

    }
}

/**
 * This is a directory service that maps from human-readable names to CorfuDB object IDs.
 * It's built using CorfuDB objects that run over hardcoded IDs (MAX_LONG and MAX_LONG-1).
 */
class DirectoryService
{
    AbstractRuntime TR;
    CorfuDBMap<String, Long> names;
    CorfuDBCounter idctr;
    public DirectoryService(AbstractRuntime tTR)
    {
        TR = tTR;
        names = new CorfuDBMap(TR, Long.MAX_VALUE);
        idctr = new CorfuDBCounter(TR, Long.MAX_VALUE-1);

    }

    /**
     * Returns a unique ID. This ID is guaranteed to be unique
     * system-wide with respect to other IDs generated across the system
     * by the getUniqueID call parameterized with a streamfactory running over
     * the same log address space. It's implemented by appending an entry
     * to the underlying log and returning the timestamp/position.
     *
     * Note: it is not guaranteed to be unique with respect to IDs returned
     * by nameToStreamID.
     *
     * @param sf StreamFactory to use
     * @return system-wide unique ID
     */
    public static long getUniqueID(StreamFactory sf)
    {
        Stream S = sf.newStream(Long.MAX_VALUE-2);
        HashSet hs = new HashSet(); hs.add(Long.MAX_VALUE-2);
        return (Long)S.append("DummyString", hs); //todo: remove the cast
    }


    /**
     * Maps human-readable name to object ID. If no such human-readable name exists already,
     * a new mapping is created.
     *
     * @param X
     * @return
     */
    public long nameToStreamID(String X)
    {
        System.out.println("Mapping " + X);
        long ret;
        while(true)
        {
            TR.BeginTX();
            if (names.containsKey(X))
                ret = names.get(X);
            else
            {
                ret = idctr.read();
                idctr.increment();
                names.put(X, ret);
            }
            if(TR.EndTX()) break;
        }
        System.out.println("Mapped " + X + " to " + ret);
        return ret;
    }

}




/**
 * This tester implements a bipartite graph over two maps, where an edge exists between integers map1:X and map2:Y
 * iff map1:X == Y and map2:Y==X. The code uses transactions across the maps to add and remove edges,
 * ensuring that the graph is always in a consistent state.
 */
class TXTesterThread implements Runnable
{
    private static Logger dbglog = LoggerFactory.getLogger(TXTesterThread.class);

    TXRuntime cr;
    CorfuDBMap<Integer, Integer> map1;
    CorfuDBMap<Integer, Integer> map2;
    int numkeys;
    int numops;

    public TXTesterThread(CorfuDBMap<Integer, Integer> tmap1, CorfuDBMap<Integer, Integer> tmap2, TXRuntime tcr)
    {
        this(tmap1, tmap2, tcr, 10, 100);
    }

    public TXTesterThread(CorfuDBMap<Integer, Integer> tmap1, CorfuDBMap<Integer, Integer> tmap2, TXRuntime tcr, int tnumkeys, int tnumops)
    {
        map1 = tmap1;
        map2 = tmap2;
        cr = tcr;
        numkeys = tnumkeys;
        numops = tnumops;
    }

    public boolean check_consistency()
    {
        boolean consistent = true;
        cr.BeginTX();
        for(int i=0;i<numkeys;i++)
        {
            if(map1.containsKey(i))
            {
                if(!map2.containsKey(map1.get(i)) || map2.get(map1.get(i))!=i)
                {
                    consistent = false;
                    break;
                }
            }
            if(map2.containsKey(i))
            {
                if(!map1.containsKey(map2.get(i)) || map1.get(map2.get(i))!=i)
                {
                    consistent = false;
                    break;
                }
            }
        }
        //todo: fix this -- it'll fail with multiple clients
        if(!cr.EndTX()) throw new RuntimeException("Consistency check aborted...");
        return consistent;
    }

    public void run()
    {
        int numcommits = 0;
        System.out.println("starting thread");
        if(numkeys<2) throw new RuntimeException("minimum number of keys for test is 2");
        for(int i=0;i<numops;i++)
        {
            long curtime = System.currentTimeMillis();
            dbglog.debug("Tx starting...");
            int x = (int) (Math.random() * numkeys);
            int y = x;
            while(y==x)
                y = (int) (Math.random() * numkeys);
            System.out.println("Creating an edge between " + x + " and " + y);
            cr.BeginTX();
            if(map1.containsKey(x)) //if x is occupied, delete the edge from x
            {
                map2.remove(map1.get(x));
                map1.remove(x);
            }
            else if(map2.containsKey(y)) //if y is occupied, delete the edge from y
            {
                map1.remove(map2.get(y));
                map2.remove(y);
            }
            else
            {
                map1.put(x, y);
                map2.put(y, x);
            }
            if(cr.EndTX()) numcommits++;
            dbglog.debug("Tx took {}", (System.currentTimeMillis()-curtime));
/*            try
            {
                Thread.sleep((int)(Math.random()*1000.0));
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }*/
        }
        System.out.println("Tester thread is done: " + numcommits + " commits out of " + numops);
    }

}




class TesterThread implements Runnable
{

    CorfuDBObject cob;
    public TesterThread(CorfuDBObject tcob)
    {
        cob = tcob;
    }

    public void run()
    {
        System.out.println("starting thread");
        for(int i=0;i<100;i++)
        {
            if(cob instanceof CorfuDBCounter)
            {
                CorfuDBCounter ctr = (CorfuDBCounter)cob;
                ctr.increment();
                System.out.println("counter value = " + ctr.read());
            }
            else if(cob instanceof CorfuDBMap)
            {
                CorfuDBMap<Integer, String> cmap = (CorfuDBMap<Integer, String>)cob; //can't do instanceof on generics, have to guess
                int x = (int) (Math.random() * 1000.0);
                System.out.println("changing key " + x + " from " + cmap.put(x, "ABCD") + " to " + cmap.get(x));
            }
/*            try
            {
                Thread.sleep((int)(Math.random()*1000.0));
            }
            catch(Exception e)
            {
                throw new RuntimeException(e);
            }*/

        }
    }
}

//todo: custom serialization + unit tests
class Pair<X, Y> implements Serializable
{
    final X first;
    final Y second;
    Pair(X f, Y s)
    {
        first = f;
        second = s;
    }

    public boolean equals(Pair<X,Y> otherP)
    {
        if(otherP==null) return false;
        if(((first==null && otherP.first==null) || (first!=null && first.equals(otherP.first))) //first matches up
                && ((second==null && otherP.second==null) || (second!=null && (second.equals(otherP.second))))) //second matches up
            return true;
        return false;
    }
}

//todo: custom serialization + unit tests
class Triple<X,Y,Z> implements Serializable
{
    final X first;
    final Y second;
    final Z third;
    Triple(X f, Y s, Z t)
    {
        first = f;
        second = s;
        third = t;
    }

    public boolean equals(Triple<X,Y,Z> otherT)
    {
        if(otherT==null) return false;
        if((((first==null && otherT.first==null)) || (first!=null && first.equals(otherT.first))) //first matches up
            && (((second==null && otherT.second==null)) || (second!=null && second.equals(otherT.second))) //second matches up
            && (((second==null && otherT.second==null)) || (second!=null && second.equals(otherT.second)))) //third matches up
            return true;
        return false;
    }
}

class BufferStack implements Serializable //todo: custom serialization
{
    private Stack<byte[]> buffers;
    private int totalsize;
    public BufferStack()
    {
        buffers = new Stack<byte[]>();
        totalsize = 0;
    }
    public BufferStack(byte[] initialbuf)
    {
        this();
        push(initialbuf);
    }
    public void push(byte[] buf)
    {
        buffers.push(buf);
        totalsize += buf.length;
    }
    public byte[] pop()
    {
        byte[] ret = buffers.pop();
        if(ret!=null)
            totalsize -= ret.length;
        return ret;
    }
    public byte[] peek()
    {
        return buffers.peek();
    }
    public int flatten(byte[] buf)
    {
        if(buffers.size()==0) return 0;
        if(buf.length<totalsize) throw new RuntimeException("buffer not big enough!");
        if(buffers.size()>1) throw new RuntimeException("unimplemented");
        System.arraycopy(buffers.peek(), 0, buf, 0, buffers.peek().length);
        return buffers.peek().length;
    }
    public byte[] flatten()
    {
        if(buffers.size()==1) return buffers.peek();
        else throw new RuntimeException("unimplemented");
    }
    public int numBufs()
    {
        return buffers.size();
    }
    public int numBytes()
    {
        return totalsize;
    }
    public static BufferStack serialize(Serializable obj)
    {
        try
        {
            //todo: custom serialization
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(obj);
            byte b[] = baos.toByteArray();
            oos.close();
            return new BufferStack(b);
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    public Object deserialize()
    {
        try
        {
            //todo: custom deserialization
            ByteArrayInputStream bais = new ByteArrayInputStream(this.flatten());
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object obj = ois.readObject();
            return obj;
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
        catch(ClassNotFoundException ce)
        {
            throw new RuntimeException(ce);
        }
    }
    //todo: this is a terribly inefficient translation from the buffer
    //representation at the runtime layer to the one used by the logging layer
    //we need a unified representation
    public List<ByteBuffer> asList()
    {
        List<ByteBuffer> L = new LinkedList<ByteBuffer>();
        L.add(ByteBuffer.wrap(this.flatten()));
        return L;
    }
}



class StreamTester implements Runnable
{
    Stream sb;
    int numops = 10000;
    public StreamTester(Stream tsb, int nops)
    {
        sb = tsb;
        numops = nops;
    }
    public void run()
    {
        System.out.println("starting sb tester thread");
        for(int i=0;i<numops;i++)
        {
            byte x[] = new byte[5];
            Set<Long> T = new HashSet<Long>();
            T.add(new Long(5));
            sb.append(new BufferStack(x), T);
        }
    }
}






/*class CorfuDBRegister implements CorfuDBObject
{
	ByteBuffer converter;
	int registervalue;
	CorfuDBRuntime TR;
	long oid;
	public long getID()
	{
		return oid;
	}

	public CorfuDBRegister(CorfuDBRuntime tTR, long toid)
	{
		registervalue = 0;
		TR = tTR;
		converter = ByteBuffer.wrap(new byte[minbufsize]); //hardcoded
		oid = toid;
		TR.registerObject(this);
	}
	public void upcall(BufferStack update)
	{
//		System.out.println("dummyupcall");
		converter.put(update.pop());
		converter.rewind();
		registervalue = converter.getInt();
		converter.rewind();
	}
	public void write(int newvalue)
	{
//		System.out.println("dummywrite");
		converter.putInt(newvalue);
		byte b[] = new byte[minbufsize]; //hardcoded
		converter.rewind();
		converter.get(b);
		converter.rewind();
		TR.updatehelper(new BufferStack(b), oid);
	}
	public int read()
	{
//		System.out.println("dummyread");
		TR.queryhelper(oid);
		return registervalue;
	}
	public int readStale()
	{
		return registervalue;
	}
}
*/

/*class PerfCounter
{
    String description;
    AtomicLong sum;
    AtomicInteger num;
    public PerfCounter(String desc)
    {
        sum = new AtomicLong();
        num = new AtomicInteger();
        description = desc;
    }
    public long incrementAndGet()
    {
        //it's okay for this to be non-atomic, since these are just perf counters
        //but we do need to get exact numbers eventually, hence the use of atomiclong/integer
        num.incrementAndGet();
        return sum.incrementAndGet();
    }
    public long addAndGet(long val)
    {
        num.incrementAndGet();
        return sum.addAndGet(val);
    }
}*/