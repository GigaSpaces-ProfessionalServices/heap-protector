package com.gigaspaces.heapprotector;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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

import com.gigaspaces.test.heapprotector.domain.Order3;
import com.j_spaces.core.client.SQLQuery;
import com.j_spaces.jdbc.driver.GConnection;

public class EvictionManager implements InitializingBean, NotificationListener, SpaceBeforeBackupListener, SpaceAfterPrimaryListener {
	private int EVICTION_BATCH_SIZE = 100;
	private final int MAX_EVICTION_BATCH_SIZE = 20000;

	private int CURRENT_YIELD_TIME = 500;

	private int SAMPLING_TIME = 2000;

	private final int EVICTION_BATCH_TO_SLEEP_TIME_FACTOR = 8;
	
	private final int MAX_EVICTION_CYCLS = 100;

	private static final Logger logger = Logger.getLogger(EvictionManager.class.getName());

	private GigaSpace gs = null;
	private PlatformTransactionManager tm = null;

	private MemoryPoolMXBean heapMemory = null;
	private EvictionConfig ec = null;
	private AtomicBoolean evictionInProgress = new AtomicBoolean(false);
	private long evictionStartValue = 0;
	private long evictionStopValue = 0;
	boolean isPrimary;
	
	private Map<String,Integer> minClassInstanceCountThreshold = new HashMap<String,Integer>();
	private Map<String,Integer> maxClassInstanceCountThreshold = new HashMap<String,Integer>();
	GConnection connection;
	
	private void initClassInstanceCountThresholds() 
	{
		Set<String> keys = ec.getClassInstanceCountThreshold().keySet();
		for (Iterator iterator = keys.iterator(); iterator.hasNext();) {
			String key = (String) iterator.next();
			String min_max = ec.getClassInstanceCountThreshold().get(key);
			String parts[] = min_max.split(",");
			minClassInstanceCountThreshold.put(key,Integer.valueOf(parts[0]));
			maxClassInstanceCountThreshold.put(key,Integer.valueOf(parts[1]));
			
			if (minClassInstanceCountThreshold.get(key) > maxClassInstanceCountThreshold.get(key))
			{
				String mes = "Wrong min/max Thresholds for class:" + key + " - min value:"+parts[0] +" - max value:"+parts[1];
				logger.info(mes );
				throw new RuntimeException(mes);
			}
			
		}
		
		
	}
	
	// invoked before a space becomes backup
	public void onBeforeBackup(BeforeSpaceModeChangeEvent event) {
		isPrimary = false;
	}

	// invoked after a space becomes primary
	public void onAfterPrimary(AfterSpaceModeChangeEvent event) {
		isPrimary = true;
		setJVMUsageThreshold();
		initClassInstanceCountThresholds();
		
		try {
			connection = GConnection.getInstance(gs.getSpace());
			connection.setUseSingleSpace(true);
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public EvictionConfig getEc() {
		return ec;
	}

	public void setEc(EvictionConfig ec) {
		this.ec = ec;
	}

	public PlatformTransactionManager getTm() {
		return tm;
	}

	public void setTm(PlatformTransactionManager tm) {
		this.tm = tm;
	}

	public GigaSpace getGs() {
		return gs;
	}

	public void setGs(GigaSpace gs) {
		this.gs = gs;
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
				evictionStartValue = (long) (maxMemory * (ec.getEvictionStartThreshold() / 100));
				evictionStopValue = (long) (maxMemory * (ec.getEvictionStopThreshold() / 100));

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
		Map<String, Integer> classEvictedCount = new HashMap<String, Integer>();
		Map<String, Integer> classEvictedDelta = new HashMap<String, Integer>();

		Map<String, Integer> classCountStart = SpaceUtil.getSpaceClassesInstanceCount(gs);
		Thread.sleep(SAMPLING_TIME);
		Map<String, Integer> classCountEnd = SpaceUtil.getSpaceClassesInstanceCount(gs);

		long evictStartTime = System.currentTimeMillis();
		long evictEndTime = 0L;

		long evictionStartMemoryUsage = heapMemory.getUsage().getUsed();
		long currentMemoryUsage = evictionStartMemoryUsage;
		int evictionCycle = 0;

		// Keep evicting until eviction limit is reached or no obects to evict
		// or read max eviction iterations
		while (currentMemoryUsage > evictionStopValue) {
			evictionCycle++;
			int totalEvicted = 0;
			if (evictionCycle > MAX_EVICTION_CYCLS) {
				break;
			}

			Iterator<String> iter = classCountEnd.keySet().iterator();
			while (iter.hasNext()) {
				String className = iter.next();

				// check if root class
				boolean isBase = SpaceUtil.isBaseClass(gs, className);
				
				if (isBase)
				{
					logger.info(">>>>> " + className + " is a base class - skip");
					continue;
				}
				// we already had an eviction cycle
				if (classEvictedCount.containsKey(className)) {
					// lets increase the batch size to speed up cycle time
					EVICTION_BATCH_SIZE = classEvictedCount.get(className) + (2 * classEvictedDelta.get(className));
					classEvictedCount.put(className , EVICTION_BATCH_SIZE);
				} else {
					// first cycle
					int entryCountStart = 0;
					// there is a chance the class won't be here
					if (classCountStart.containsKey(className))
						entryCountStart = classCountStart.get(className);

					int entryCountEnd = classCountEnd.get(className);
					EVICTION_BATCH_SIZE = (entryCountEnd - entryCountStart) / (SAMPLING_TIME/1000);
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

//				Object obj[] = null;
				try {
					boolean evict = true;
					boolean thresholdEviction = false;
					
//					System.out.println("maxClassInstanceCountThreshold " + maxClassInstanceCountThreshold);
//					System.out.println("minClassInstanceCountThreshold " + minClassInstanceCountThreshold);
					
					// check min and max threshold
					if (maxClassInstanceCountThreshold.containsKey(className))
					{
						logger.info("--->>>>>> eviction based on thresholds for "+className );
						if ((classCountEnd.get(className) < maxClassInstanceCountThreshold.get(className)) & 
								(classCountEnd.get(className) > minClassInstanceCountThreshold.get(className)))
						{
							// no need to evict
							evict = false;
							logger.info("no eviction for "+className + " within thresholds");
						}
						else if (classCountEnd.get(className) > maxClassInstanceCountThreshold.get(className)) 
						{
							evict = true;
							EVICTION_BATCH_SIZE = classCountEnd.get(className) - (maxClassInstanceCountThreshold.get(className) - classEvictedDelta.get(className));
							
							if (EVICTION_BATCH_SIZE > MAX_EVICTION_BATCH_SIZE)
									EVICTION_BATCH_SIZE = MAX_EVICTION_BATCH_SIZE /2;
							
							thresholdEviction = true;
							logger.info("--->>>>>> eviction thresholds used for "+className );
						}
					}
					
					if (evict)
					{
//						SQLQuery template = new SQLQuery(className, "");
						
						// won't return anything within the object
//						template.setProjections("");
						// Clear data in smaller batches in order to not create lots
						// of garbage in space and trigger GC activity
						
						Statement st = connection.createStatement();
						int deletedCount = st.executeUpdate("delete from "+className + " where rownum<" + EVICTION_BATCH_SIZE);
						st.close();
//						obj = gs.takeMultiple(template, EVICTION_BATCH_SIZE);
						classEvictedCount.put(className, deletedCount);
						totalEvicted = totalEvicted + deletedCount;
						logger.info("evicted " + deletedCount + " objects - EVICTION_BATCH_SIZE:" + EVICTION_BATCH_SIZE);
						// update instance count
						if (thresholdEviction)
							classCountEnd.put(className, SpaceUtil.getEntryCount(gs, className));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				// Commit the Transaction
				tm.commit(status);
			} // end loop on classes

			if (totalEvicted > 0) {
				Runtime.getRuntime().gc();
				
				CURRENT_YIELD_TIME = totalEvicted / EVICTION_BATCH_TO_SLEEP_TIME_FACTOR;

				// lets sleep allowing GS to do its thing
				try {
					Thread.sleep(CURRENT_YIELD_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			currentMemoryUsage = heapMemory.getUsage().getUsed();
			logger.info("Current Memory Usage [" + currentMemoryUsage + "]");

		}
		evictEndTime = System.currentTimeMillis();
		long evictionCycleDuration = evictEndTime -evictStartTime;
		logger.info("Eviction completed. Starting Memory Usage [" + evictionStartMemoryUsage+ "], Ending Memory Usage [" + currentMemoryUsage + "] - Total eviction duration[ms]:" + evictionCycleDuration);
	}
}
