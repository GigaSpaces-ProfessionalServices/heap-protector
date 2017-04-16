package com.gigaspaces.test.heapprotector.client;

import java.rmi.RemoteException;
import java.util.Random;
import java.util.logging.Logger;

import net.jini.core.lease.Lease;
import net.jini.core.lease.UnknownLeaseException;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;
import org.openspaces.core.transaction.manager.LocalJiniTxManagerConfigurer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import com.gigaspaces.test.heapprotector.domain.Order;
import com.gigaspaces.test.heapprotector.domain.OrderType;
import com.j_spaces.core.IJSpace;
import com.j_spaces.core.LeaseContext;
import com.j_spaces.core.client.UpdateModifiers;

public class RetrieveAndManageLease {

	Logger logger = Logger.getLogger(this.getClass().getName());
	GigaSpace gigaSpace = null;
	PlatformTransactionManager ptm = null;
	int batchSize = 1000;

	static Random r = new Random();

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java RetrieveAndManageLease <space URL>");
			System.exit(1);
		}

		RetrieveAndManageLease testClient = new RetrieveAndManageLease(args[0]);
	}

	/**
	 * Here we have the only constructor for this example TestClient
	 * 
	 * @param url
	 *            : the url to the space
	 */
	public RetrieveAndManageLease(String url) {
		// connect to the space using its URL

		// connect to the space using its URL
		IJSpace space = new UrlSpaceConfigurer(url).space();
		// use gigaspace wrapper to for simpler API
		this.gigaSpace = new GigaSpaceConfigurer(space).gigaSpace();

		try {
			ptm = new LocalJiniTxManagerConfigurer(space).transactionManager();
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(-1);
		}

		this.gigaSpace = new GigaSpaceConfigurer(space).transactionManager(ptm)
				.gigaSpace();

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

			Order o = new Order();

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

				gigaSpace.write(o, Lease.FOREVER, 10000,
						UpdateModifiers.UPDATE_OR_WRITE);
				
				ptm.commit(status);

			} else {

				counter++;
				batchOrder[batchCount++] = o;

				if (batchCount % batchSize == 0) {

					DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
					// configure the definition...
					TransactionStatus status = ptm.getTransaction(definition);

					try {
						
						gigaSpace.writeMultiple(batchOrder, Lease.FOREVER,
								UpdateModifiers.UPDATE_OR_WRITE);

						ptm.commit(status);

					} catch (Exception e) {
						ptm.rollback(status);
						e.printStackTrace();
					}
					
					//Retrieve all processed low priority processed orders and update the lease 
					Order template = new Order();
					
					template.setType(OrderType.LOW);
					template.setProcessed(true);

					Order[] processedLowPriorityOrders = gigaSpace.readMultiple(template, 1000);
					
					//Update the lease to expire in 1 second
					gigaSpace.writeMultiple(processedLowPriorityOrders, 
							1000,					// Update the Lease to 1 second
							UpdateModifiers.UPDATE_OR_WRITE); // Update existing object

					batchOrder = new Order[batchSize];
					batchCount = 0;

				}
			}

			
			if (counter % 10000 == 0) {
				logger.info(System.currentTimeMillis() + " Feeder WROTE "
						+ counter + " messages so far");

				if (counter > 90000) {
					try {
						Thread.sleep(60000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
}
