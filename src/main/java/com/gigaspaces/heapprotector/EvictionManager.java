package com.gigaspaces.heapprotector;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.space.mode.AfterSpaceModeChangeEvent;
import org.openspaces.core.space.mode.BeforeSpaceModeChangeEvent;
import org.openspaces.core.space.mode.SpaceAfterPrimaryListener;
import org.openspaces.core.space.mode.SpaceBeforeBackupListener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.j_spaces.core.client.SQLQuery;

public class EvictionManager implements InitializingBean, NotificationListener, SpaceBeforeBackupListener, SpaceAfterPrimaryListener {

	private final int 	INITIAL_EVICTION_BATCH_SIZE = 100;
    private int 		EVICTION_BATCH_SIZE = 100;
    private int 		EVICTION_BATCH_DELTA = 100;
    private final int 	MAX_EVICTION_BATCH_SIZE = 20000;

    private final int 	MAX_YIELD_TIME = 5000;
    private final int 	INITIAL_YIELD_TIME = 500;
    private final int 	YIELD_TIME_DELTA = 100;
    private int 		CURRENT_YIELD_TIME = 500;

    private int SAMPLING_TIME = 1000;

    private final int MAX_EVICTION_CYCLES = 100;

    private static final Logger logger = Logger.getLogger(EvictionManager.class.getName());

    private GigaSpace gigaSpace = null;
    private PlatformTransactionManager tm = null;

    private MemoryPoolMXBean heapMemory = null;
    private EvictionConfig evictionConfig = null;
    private AtomicBoolean evictionInProgress = new AtomicBoolean(false);
    private long evictionStartValue = 0;
    private long evictionStopValue = 0;
    boolean isPrimary;

	// invoked after a space becomes primary
	// see: https://docs.gigaspaces.com/xap120/the-space-notifications.html#primary-backup

	public void onAfterPrimary(AfterSpaceModeChangeEvent event) {
		isPrimary = true;
		setJVMUsageThreshold();
	}

	// invoked before a space becomes backup
    public void onBeforeBackup(BeforeSpaceModeChangeEvent event) {
        isPrimary = false;
    }

    public EvictionConfig getEvictionConfig() {
        return evictionConfig;
    }

    public void setEvictionConfig(EvictionConfig evictionConfig) {
        this.evictionConfig = evictionConfig;
    }

    public PlatformTransactionManager getTm() {
        return tm;
    }

    public void setTm(PlatformTransactionManager tm) {
        this.tm = tm;
    }

    public GigaSpace getGigaSpace() {
        return gigaSpace;
    }

    public void setGigaSpace(GigaSpace gigaSpace) {
        this.gigaSpace = gigaSpace;
    }

    @Override
    public void afterPropertiesSet() throws Exception {

    }

    /**
     * 
     * Method uses the Java Memory Management API to register for memory usage
     * notification whenever usage of Old gen pool reaches highWatermark
     * 
     * @throws RuntimeException
     *             - RunTime Exception thrown on unsupported JVM
     */
    private void setJVMUsageThreshold() throws RuntimeException {

        // Get Memory MXBean
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

        // Add current object as the listener
        NotificationEmitter ne = (NotificationEmitter) memBean;
        ne.addNotificationListener(this, null, null);

        // Get the memory pools supported by the JVM and size of each pool
        List<MemoryPoolMXBean> memPools = ManagementFactory.getMemoryPoolMXBeans();

        for (MemoryPoolMXBean pool : memPools) {

            if (pool.getType() == MemoryType.HEAP && pool.isUsageThresholdSupported()) {

                heapMemory = pool;

                long maxMemory = pool.getUsage().getMax();
                evictionStartValue = (long) (maxMemory * (evictionConfig.getEvictionStartThreshold() / 100));
                evictionStopValue = (long) (maxMemory * (evictionConfig.getEvictionStopThreshold() / 100));

                logger.info("Maximum Heap Memory : " + maxMemory);
                logger.info("Eviction Starts at heap size : " + evictionStartValue);

                try {
                    pool.setUsageThreshold(evictionStartValue);
                } catch (Exception e) {
                    logger.severe("***** Setting usage Threshold on Old Gen pool failed " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }


    @Override
    public void handleNotification(Notification n, Object handback) {

        String type = n.getType();

        if (type.equals(MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED)) {

            // Run eviction logic only if no other instance of eviction is in
            // process right now
            if (evictionInProgress.compareAndSet(false, true)) {
                // Retrieve Memory Notification information
                CompositeData cd = (CompositeData) n.getUserData();
                MemoryNotificationInfo memInfo = MemoryNotificationInfo.from(cd);

                logger.info("Eviction Process Notification received. Memory Usage before Eviction started : "
                        + memInfo.getUsage().getUsed());

                try {
                    evictObjects();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
					logger.log(Level.WARNING, e.getMessage(), e);
                    e.printStackTrace();
                }
            } else {
                logger.warning(
                        "Memory Notification Worker received another notification while eviction is in progress. "
                                + type);
            }
        } else {
            logger.warning("Memory Notification Worker received invalid notification. " + type);
        }
        // Set Eviction is not in process
        evictionInProgress.set(false);

    }

    public void evictObjects() throws Exception {
		// k, v = className, total number of objects evicted
        Map<String, Integer> classEvictedCount = new HashMap<String, Integer>();
		// k, v = className, number of objects to be evicted
        Map<String, Integer> classEvictedDelta = new HashMap<String, Integer>();

        Map<String, Integer> classCountStart = SpaceUtil.getSpaceClassesInstanceCount(gigaSpace);
        Thread.sleep(SAMPLING_TIME);
        Map<String, Integer> classCountEnd = SpaceUtil.getSpaceClassesInstanceCount(gigaSpace);

        long evictStartTime = System.currentTimeMillis();
        long evictEndTime = 0L;

        long evictionStartMemoryUsage = heapMemory.getUsage().getUsed();
        long currentMemoryUsage = evictionStartMemoryUsage;
        int evictionCycle = 0;

        // Keep evicting until eviction limit is reached or no objects to evict
        // or read max eviction iterations
        while (currentMemoryUsage > evictionStopValue) {
            evictionCycle++;
            int totalEvicted = 0;
            if (evictionCycle > MAX_EVICTION_CYCLES) {
                break;
            }

            Iterator<String> iter = classCountEnd.keySet().iterator();
            while (iter.hasNext()) {
                String className = iter.next();

                // check if root class
                boolean isBase = SpaceUtil.isBaseClass(gigaSpace, className);
                
                if (isBase)
                {
                    logger.info(">>>>> " + className + " is a base class - skip");
                    continue;
                }
                // we already had an eviction cycle
                if (classEvictedCount.containsKey(className)) {
                    // let's increase the batch size to speed up cycle time
                    EVICTION_BATCH_SIZE = classEvictedCount.get(className) + (2 * classEvictedDelta.get(className));
                    classEvictedCount.put(className , EVICTION_BATCH_SIZE);
                } else {
                    // first cycle
                    int entryCountStart = 0;
                    // there is a chance the class won't be here
                    if (classCountStart.containsKey(className))
                        entryCountStart = classCountStart.get(className);

                    int entryCountEnd = classCountEnd.get(className);
                    EVICTION_BATCH_SIZE = (entryCountEnd - entryCountStart);
                    // keep the delta to be used as the factor
                    classEvictedDelta.put(className, EVICTION_BATCH_SIZE);
                    logger.info(">>>>> we have extra " + EVICTION_BATCH_SIZE + " " + className + " objects");
                }

                // no objects added for this class , skip it
                if (EVICTION_BATCH_SIZE < 1)
                {
                    logger.info(">>>>> skipping " + className + " objects - no objects added");
                    continue;
                }
                
                if (EVICTION_BATCH_SIZE > MAX_EVICTION_BATCH_SIZE)
                    EVICTION_BATCH_SIZE = MAX_EVICTION_BATCH_SIZE / 2;

                logger.info("Eviction of entry - " + className + " started");

                // Configure Transaction definition ...
                DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
                TransactionStatus status = tm.getTransaction(definition);

                Object obj[] = null;
                try {
                    SQLQuery template = new SQLQuery(className, "");
                    
                    // won't return anything within the object
                    template.setProjections("");
                    // Clear data in smaller batches in order to not create lots
                    // of garbage in space and trigger GC activity
                    obj = gigaSpace.takeMultiple(template, EVICTION_BATCH_SIZE);
                    classEvictedCount.put(className, obj.length);
                    totalEvicted = totalEvicted + obj.length;
                    logger.info("evicted " + obj.length + " objects - EVICTION_BATCH_SIZE:" + EVICTION_BATCH_SIZE);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Commit the Transaction
                tm.commit(status);
            } // end loop on classes

            if (totalEvicted > 0) {
                Runtime.getRuntime().gc();
                CURRENT_YIELD_TIME = totalEvicted / 8;

                // lets sleep allowing GS to do its thing
                try {
                    Thread.sleep(CURRENT_YIELD_TIME);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            currentMemoryUsage = heapMemory.getUsage().getUsed();
            logger.info("Current Memory Usage [" + currentMemoryUsage + "]");
/*
            if (totalEvicted == 0) {
                try {
                    Thread.sleep(5000);
                    EVICTION_BATCH_SIZE = 1000;
                    CURRENT_YIELD_TIME = INITIAL_YIELD_TIME;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                // increase batch size
                if (EVICTION_BATCH_SIZE < MAX_EVICTION_BATCH_SIZE)
                    EVICTION_BATCH_SIZE = EVICTION_BATCH_SIZE + EVICTION_BATCH_DELTA;

                // increase yield time
                if (CURRENT_YIELD_TIME < MAX_YIELD_TIME)
                    CURRENT_YIELD_TIME = EVICTION_BATCH_SIZE / 4;
            }
*/
            // wMark.setEvictionInProgress(false);

        }
        evictEndTime = System.currentTimeMillis();
        long evictionCycleDuration = evictEndTime -evictStartTime;
        logger.info("Eviction completed. Starting Memory Usage [" + evictionStartMemoryUsage+ "], Ending Memory Usage [" + currentMemoryUsage + "] - Total eviction duration[ms]:" + evictionCycleDuration);
    }
}
