package org.corfudb.runtime.smr;

import org.corfudb.runtime.entries.IStreamEntry;
import org.corfudb.runtime.stream.IStream;
import org.corfudb.runtime.stream.ITimestamp;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Created by mwei on 5/1/15.
 */
public class SimpleSMREngine<T> implements ISMREngine<T> {

    IStream stream;
    T underlyingObject;
    ITimestamp streamPointer;
    ITimestamp lastProposal;
    HashMap<ITimestamp, CompletableFuture<Object>> completionTable;

    class SimpleSMREngineOptions implements ISMREngineOptions
    {
        CompletableFuture<Object> returnResult;

        public SimpleSMREngineOptions(CompletableFuture<Object> returnResult)
        {
            this.returnResult = returnResult;
        }
        public CompletableFuture<Object> getReturnResult()
        {
            return this.returnResult;
        }
    }

    public SimpleSMREngine(IStream stream, Class<T> type)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException
    {
        this.stream = stream;
        streamPointer = stream.getCurrentPosition();
        completionTable = new HashMap<ITimestamp,CompletableFuture<Object>>();
        underlyingObject = type.getConstructor().newInstance();
    }

    /**
     * Get the underlying object. The object is dynamically created by the SMR engine.
     *
     * @return The object maintained by the SMR engine.
     */
    @Override
    public T getObject() {
        return underlyingObject;
    }

    /**
     * Synchronize the SMR engine to a given timestamp, or pass null to synchronize
     * the SMR engine as far as possible.
     *
     * @param ts The timestamp to synchronize to, or null, to synchronize to the most
     *           recent version.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void sync(ITimestamp ts) {
        synchronized (this) {
            if (ts == null) {
                ts = stream.check();
                if (ts.compareTo(streamPointer) <= 0) {
                    //we've already read to the most recent position, no need to keep reading.
                    return;
                }
            }
            while (ts.compareTo(streamPointer) > 0) {
                try {
                    IStreamEntry entry = stream.readNextEntry();
                    ISMREngineCommand<T> function = (ISMREngineCommand<T>) entry.getPayload();
                    CompletableFuture<Object> completion = completionTable.remove(entry.getTimestamp());
                    function.accept(underlyingObject, new SimpleSMREngineOptions(completion));
                } catch (Exception e) {
                    //ignore entries we don't know what to do about.
                }
                streamPointer = stream.getCurrentPosition();
            }
        }
    }

    /**
     * Propose a new command to the SMR engine.
     *
     * @param command       A lambda (BiConsumer) representing the command to be proposed.
     *                      The first argument of the lambda is the object the engine is acting on.
     *                      The second argument of the lambda contains some TX that the engine
     *                      The lambda must be serializable.
     *
     * @param completion    A completable future which will be fulfilled once the command is proposed,
     *                      which is to be completed by the command.
     *
     * @return              The timestamp the command was proposed at.
     */
    @Override
    public ITimestamp propose(ISMREngineCommand<T> command, CompletableFuture<Object> completion) {
        try {
            ITimestamp t = stream.append(command);
            if (completion != null) { completionTable.put(t, completion); }
            lastProposal = t;
            return t;
        }
        catch (Exception e)
        {
            //well, propose is technically not reliable, so we can just silently drop
            //any exceptions.
            return null;
        }
    }

    /**
     * Get the timestamp of the most recently proposed command.
     *
     * @return A timestamp representing the most recently proposed command.
     */
    @Override
    public ITimestamp getLastProposal() {
        return lastProposal;
    }

    /**
     * Pass through to check for the underlying stream.
     *
     * @return A timestamp representing the most recently proposed command on a stream.
     */
    @Override
    public ITimestamp check() {
        return stream.check();
    }

    /**
     * Get the underlying stream ID.
     *
     * @return A UUID representing the ID for the underlying stream.
     */
    @Override
    public UUID getStreamID() {
        return stream.getStreamID();
    }
}