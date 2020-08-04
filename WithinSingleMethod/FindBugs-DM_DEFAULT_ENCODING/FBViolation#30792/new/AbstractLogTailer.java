package com.github.triceo.splitlog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;

abstract class AbstractLogTailer {

    private final LogWatch watch;

    protected AbstractLogTailer(final LogWatch watch) {
        this.watch = watch;
    }

    /**
     * Retrieve messages that this tailer has been notified of.
     * 
     * @return Messages we are aware of, in their original order.
     */
    public abstract List<Message> getMessages();

    protected LogWatch getWatch() {
        return this.watch;
    }

    public boolean isTerminated() {
        return this.watch.isTerminated(this);
    }

    /**
     * Notify the tailer of a new message in the watched log. Must never be
     * called by users, just from the library code.
     * 
     * @param msg
     *            The message.
     */
    protected abstract void notifyOfMessage(Message msg);

    /**
     * Mark the current location in the tail by a custom message. It is up to
     * the implementors to decide whether or not a tag inserted at the same time
     * twice is inserted twice, or the second tag overwrites the first.
     * 
     * @param tagLine
     *            Text of the message.
     */
    public abstract void tag(String tagLine);

    /**
     * Will block until a message arrives, for which the condition is true.
     * 
     * @param condition
     *            Condition that needs to be true for the method to unblock.
     * @return Whether the message actually arrived, or the method unblocked due
     *         to some other reason.
     */
    public abstract boolean waitFor(MessageCondition condition);

    /**
     * Will block until a message arrives, for which the condition is true. If
     * none arrives before the timeout, it unblocks anyway.
     * 
     * @param condition
     *            Condition that needs to be true for the method to unblock.
     * @return Whether the message actually arrived, or the method unblocked due
     *         to some other reason.
     */
    public abstract boolean waitFor(MessageCondition condition, long timeout, TimeUnit unit);

    /**
     * Will output the log, including tags, into a stream.
     * 
     * @param stream
     *            Target.
     * @return True if written, false otherwise.
     */
    public boolean write(final OutputStream stream) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));
            for (final Message msg : this.getMessages()) {
                w.write(msg.toString());
                w.newLine();
            }
            return true;
        } catch (final IOException ex) {
            return false;
        } finally {
            IOUtils.closeQuietly(w);
        }
    }

}
