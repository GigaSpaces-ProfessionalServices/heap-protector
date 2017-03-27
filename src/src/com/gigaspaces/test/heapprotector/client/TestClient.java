package com.gigaspaces.test.heapprotector.client;

import java.util.Random;
import java.util.logging.Logger;

import net.jini.core.lease.Lease;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;
//import org.openspaces.core.transaction.manager.LocalJiniTxManagerConfigurer;

import org.openspaces.core.transaction.manager.DistributedJiniTxManagerConfigurer;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.gigaspaces.heapprotector.SpaceUtil;
import com.gigaspaces.test.heapprotector.domain.*;
import com.j_spaces.core.IJSpace;
import com.j_spaces.core.client.UpdateModifiers;

public class TestClient {

	Logger logger = Logger.getLogger(this.getClass().getName());
	GigaSpace gigaSpace = null;
	PlatformTransactionManager ptm = null;
	int batchSize = 1000;

	static Random r = new Random();

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java TestClient <space URL>");
			System.exit(1);
		}

		TestClient testClient = new TestClient(args[0]);
	}

	/**
	 * Here we have the only constructor for this example TestClient
	 * 
	 * @param url
	 *            : the url to the space
	 */
	public TestClient(String url) {
		// connect to the space using its URL

		// connect to the space using its URL
		IJSpace space = new UrlSpaceConfigurer(url).space();
		// use gigaspace wrapper to for simpler API
		this.gigaSpace = new GigaSpaceConfigurer(space).gigaSpace();

		try {
			ptm = new DistributedJiniTxManagerConfigurer().transactionManager();
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(-1);
		}

		this.gigaSpace = new GigaSpaceConfigurer(space).transactionManager(ptm).gigaSpace();

		try {
			feed(); // run the feeder (start feeding)
		} catch (NumberFormatException e) {
			logger.info("Invalid argument passed, count not a number");
		}

	}

	public void feed() {

		logger.info("Feeder Starting write of messages");
		long startTime = System.currentTimeMillis();

		Order[] batchOrder = new Order[batchSize];
		int batchCount = 0;
		int counter = 0;
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

			o.setId(counter);
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

				counter++;
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

			if (counter % 100== 0) {
				logger.info(System.currentTimeMillis() + " Feeder WROTE "+ counter + " messages so far");

				if (counter > 1500000)
				{
					try {
						Thread.sleep(r.nextInt(100));
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
