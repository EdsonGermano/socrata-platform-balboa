package com.socrata.balboa.metrics.data;

import com.socrata.balboa.metrics.data.impl.TimeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BalboaFastFailCheck
 * If a DataStore is down; provide some logic
 * for failing writes immediately until a
 * timeout occurs. If the failure is still occurring
 * increase the timeout.
 */
public class BalboaFastFailCheck {
    private static Log log = LogFactory.getLog(BalboaFastFailCheck.class);
    private static final BalboaFastFailCheck INSTANCE = new BalboaFastFailCheck(new TimeService());

    // Initial failure delay
    public final static long INITIAL_FAILURE_DELAY_MS = 1000;
    // max time to permit a fast fail before allowing a retry
    public final static long MAX_FAILURE_DELAY_MS = 30000;


    private AtomicLong failFastUntilTime = new AtomicLong(0);
    private int mult = 1;
    private volatile boolean inFailureMode = false;
    private Object lock = new Object();


    private final TimeService timeService;

    public static BalboaFastFailCheck getInstance() {
        return INSTANCE;
    }

    // package protected for tests
    BalboaFastFailCheck(TimeService timeService) {
        this.timeService = timeService;
    }

    /**
     * Indicate that the DataStore is currently failing
     */
    public void markFailure() {
        synchronized (lock) {
            inFailureMode = true;
            long delayTime = INITIAL_FAILURE_DELAY_MS * mult;
            if (delayTime > MAX_FAILURE_DELAY_MS) {
                delayTime = MAX_FAILURE_DELAY_MS;
            }
            failFastUntilTime.set(timeService.currentTimeMillis() + delayTime);
            mult *= 2;
            log.error("Entered fast fail mode with delay " + delayTime);
        }
    }

    /**
     * Clears the failure mode
     */
    public void markSuccess() {
        if (inFailureMode) {
            synchronized (lock) {
                inFailureMode = false;
                failFastUntilTime.set(0);
                mult = 1;
                log.error("Exiting fast failure mode!");
            }
        }
    }

    /**
     * Returns true if this DataStore is in a happy place
     */
    public boolean proceed() {
        return !inFailureMode || failFastUntilTime.get() < timeService.currentTimeMillis();
    }

    public boolean isInFailureMode() {
        return inFailureMode;
    }
}