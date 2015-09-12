package org.corfudb.samples;

import org.corfudb.infrastructure.SimpleLogUnitServer;
import org.corfudb.infrastructure.StreamingSequencerServer;
import org.corfudb.runtime.CorfuDBRuntime;
import org.corfudb.runtime.protocols.logunits.CorfuDBSimpleLogUnitProtocol;
import org.corfudb.runtime.view.CorfuDBView;
import org.corfudb.runtime.view.ICorfuDBInstance;
import org.corfudb.util.CorfuDBFactory;
import org.corfudb.util.CorfuInfrastructureBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by dalia on 9/11/15.
 */
public class CorfuInOne {

    public static void main(String args[]) {

        Map<String, Object> luConfigMap = new HashMap<String,Object>() {
            {
                put("capacity", 200000);
                put("ramdisk", true);
                put("pagesize", 4096);
                put("trim", 0);
            }
        };
        CorfuInfrastructureBuilder cbuilder = new CorfuInfrastructureBuilder().
                addLoggingUnit(7002, 0, SimpleLogUnitServer.class, "cdbslu", luConfigMap).
                addSequencer(7001, StreamingSequencerServer.class, "cdbsts", null).
                start(7000) ;

        CorfuDBRuntime cdr = CorfuDBRuntime.getRuntime("http://localhost:7000/corfu");

        ICorfuDBInstance cinstance = cdr.getLocalInstance();

       /* check health of Configuration Master by trying to retrieve view
         */

        long timeout = 10000;
        System.out.println("Trying simple connection with Corfu components: config-master, sequencer, and logging units (will timeout in " + timeout/1000 + " secs)...");

        Thread b = new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("trying to connect to config-master...");
                CorfuDBView view = cdr.getView();

                System.out.println("trying to ping all view components...: " + view.isViewAccessible() );
                synchronized (this) { notify();}
            } } );
        b.start();

        synchronized (b) {
            try {
                b.wait(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println("HelloCorfu test finished successfully");

    }
}