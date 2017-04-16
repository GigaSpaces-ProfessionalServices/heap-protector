package com.gigaspaces.test.heapprotector.client;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;
//import org.openspaces.core.transaction.manager.LocalJiniTxManagerConfigurer;

import org.openspaces.core.transaction.manager.DistributedJiniTxManagerConfigurer;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.gigaspaces.test.heapprotector.domain.*;
import com.j_spaces.core.IJSpace;

public class TestClient implements Runnable{

	Logger logger = Logger.getLogger(this.getClass().getName());
	static GigaSpace gigaSpace = null;
	static PlatformTransactionManager ptm = null;
	int batchSize = 1000;
	static int threadCount = 3 ;
	static Random r = new Random();

	public static void main(String[] args) throws Exception{
		if (args.length < 1) {
			System.err.println("Usage: java TestClient <space URL>");
			System.exit(1);
		}
		init(args[0]);
		Thread[] threads = new Thread[threadCount];
		for (int i = 0; i < threads.length; i++) {
			threads[i] = new Thread(new TestClient());
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].start();
		}
		for (int i = 0; i < threads.length; i++) {
			threads[i].join();
		}		
		
	}


	public TestClient() {
	}
	
	public static void init(String url) {
		// connect to the space using its URL
		IJSpace space = new UrlSpaceConfigurer(url).space();
		gigaSpace = new GigaSpaceConfigurer(space).gigaSpace();

		try {
			ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(-1);
		}

		gigaSpace = new GigaSpaceConfigurer(space).transactionManager(ptm).gigaSpace();
		
		counter = new AtomicInteger(gigaSpace.count(null) );
	}

	static AtomicInteger counter = null;

	public void run() {

		logger.info("Feeder Starting write of messages");
		long startTime = System.currentTimeMillis();

		Order[] batchOrder = new Order[batchSize];
		int batchCount = 0;
		
		Random r = new Random();

		while (true) {
			int orderClassType = r.nextInt(4) + 1;
			Order o = null;
			//if (orderClassType==0)
				//o = new Order();
			if (orderClassType==1)
				o = new Order1();
			else if (orderClassType==2)
				o = new Order2();
			else if (orderClassType==3)
				o = new Order3();
			else if (orderClassType==4)
				o = new Order4();

			o.setId(counter.incrementAndGet());
			o.setRawData("Raw Order data " + counter);
			o.setProcessed(false);
			o.setOrderTime(System.currentTimeMillis());

			switch (r.nextInt(10)) {

			case 1:
			case 2:
				o.setType(OrderType.LOW);
				break;
			case 9:
				o.setType(OrderType.VIP);
				break;
			default:
				o.setType(OrderType.NORMAL);
			}
			
			if (batchSize == 0) {

				DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
				// configure the definition...
				TransactionStatus status = ptm.getTransaction(definition);
				gigaSpace.write(o);
				ptm.commit(status);

			} else {
				batchOrder[batchCount++] = o;

				if (batchCount % batchSize == 0) {
					DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
					// configure the definition...
					TransactionStatus status = ptm.getTransaction(definition);

					try {
						gigaSpace.writeMultiple(batchOrder);
					} catch (Exception e) {
						ptm.rollback(status);
						e.printStackTrace();
					}
					
					ptm.commit(status);

					batchOrder = new Order[batchSize];
					batchCount = 0;
				}
			}
			
			if (counter.get() % 500 == 0) {
				logger.info(System.currentTimeMillis() + " TID:" +Thread.currentThread().getId() + " - Feeder WROTE "+ counter + " messages so far");
				
				if (counter.get() > 12000000)
				{
					try {
						Thread.sleep(r.nextInt(500));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

}
