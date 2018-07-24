package com.click2sync.rc.srv.sapb1;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import org.json.simple.JSONObject;

public class Main {
	
	final String configFile = "./config.properties";
	Properties config;
	SAPB1Proxy sap;
	C2SProxy c2s;
	Long longpauseMillis = 10000L;
	Long normalpauseMillis = 1000L;
	static Main app;
	
	Main(){
		
		c2s = new C2SProxy(this);
		sap = new SAPB1Proxy(this);
		config = new Properties();
		InputStream input;
		try {
			input = new FileInputStream(configFile);
			config.load(input);
			longpauseMillis = Long.parseLong(config.getProperty("longpausemillis"));
			normalpauseMillis = Long.parseLong(config.getProperty("normalpausemillis"));
		} catch (NumberFormatException e) {
			ServiceLogger.error(new C2SRCServiceException("Could not read pause time settings from configuration: "+e.getMessage()));
		} catch (FileNotFoundException e) {
			ServiceLogger.error(new C2SRCServiceException("Configuration file not found: "+e.getMessage()));
		} catch (IOException e) {
			ServiceLogger.error(new C2SRCServiceException("Could not access configuration file: "+e.getMessage()));
		}
		
	}
	
	public void loop() throws InterruptedException {
		
		try {
			work();
			pauseWork(true);
		} catch (C2SUnreachableException e) {
			ServiceLogger.error(e);
			pauseWork(true);
		} catch (NoSAPB1Exception e) {
			ServiceLogger.error(e);
			pauseWork(true);
		} catch (C2SRCServiceException e) {
			ServiceLogger.error(e);
			pauseWork(true);
		}
		
	}
	
	public void pauseWork(boolean longPause) throws InterruptedException {
		
		if(longPause) {
			Thread.sleep(longpauseMillis);
		} else {
			Thread.sleep(normalpauseMillis);
		}
		
	}
	
	public void senseEnvironmentForSAPServices() throws NoSAPB1Exception {
		
		sap.sense();
		
	}
	
	public void senseEnvironmentForC2SReachability() throws C2SUnreachableException {
		
		c2s.sense();
		
	}
	
	public static void abstractLoop() {
		
		ServiceLogger.log("loop");
		if(app == null) {
			app = new Main();
			Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHook()));
			try {
				app.senseEnvironmentForSAPServices();
				app.senseEnvironmentForC2SReachability();
			} catch (NoSAPB1Exception e) {
				ServiceLogger.error(e);
				ServiceLogger.error(e);
				try {
					app.pauseWork(true);
					app = null;
				} catch (InterruptedException e1) {
					ServiceLogger.error(e1);
				}
				return;
			} catch (C2SUnreachableException e) {
				ServiceLogger.error(e);
				try {
					app.pauseWork(true);
					app = null;
				} catch (InterruptedException e1) {
					ServiceLogger.error(e1);
				}
				return;
			}
		}
		try {
			app.loop();
		} catch (InterruptedException e) {
			ServiceLogger.error(e);
		}
		
	}

	public static void main(String[] args) {
		
		if(args.length > 0) {//debugging
			boolean cli = false;
			boolean testbpgen = false;
			for(int i=0; i<args.length; i+=1) {
				System.out.println(args[i]);
				if(args[i].equals("--cli")) {
					cli = true;
				}else if(args[i].equals("--testbpgen")) {
					testbpgen = true;
				}
			}
			if(cli) {
				if(testbpgen) {
					app = new Main();
					app.testbpgen();
				}else {
					System.out.println("Could not understand the intended operation");
				}
			}else {
				System.out.println("Could not understand the intended operation");
			}
			
		}else {//normal service run
			while(true) {
				try {
					abstractLoop();
				}catch(Exception e) {
					ServiceLogger.error(e);
				}
			}
		}

	}
	
	private void testbpgen() {
		
		try {
			sap.sense();
			//sap.testbpgen();
		} catch (NoSAPB1Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private boolean checkIfWeAreFetchingRemote() throws C2SUnreachableException, C2SRCServiceException {
		
		return c2s.checkIfFetchingRemote();
	}
	
	private boolean checkIfWeArePullingToRemote() throws C2SUnreachableException, C2SRCServiceException {
		
		return c2s.checkIfPullingToRemote();
	}
	
	private void fetchRemote() throws C2SUnreachableException,NoSAPB1Exception, InterruptedException {
		
		boolean uploadProducts = true;
		boolean uploadOrders = true;
		ServiceLogger.log("Fetching remote...");
		String strategy = c2s.getStrategy();
		String upstreamstatus = c2s.getUpstreamStatus();
		String entity = c2s.getEntity();
		Long offsetdate = c2s.getCursorOffset();
		
		if(upstreamstatus != null && upstreamstatus.equals("waiting")) {
			//if is waiting, good, change to initialized
			c2s.setInitializeUpload(strategy);
			upstreamstatus = c2s.getUpstreamStatus();
			//(if webonline responds error, then notify of strange behavior to server)
		}
		if(upstreamstatus != null && upstreamstatus.equals("initialized")) {
			//if is initialized, check cursor
			//from cursor start upload
			//when packet uploaded, renew offsets
			if(entity != null && entity.equals("products")) {
				if(uploadProducts) {
					if(strategy != null && strategy.equals("pingsample")) {
						sap.setProductsStreamCursor(offsetdate);
						if(sap.hasMoreProducts()) {
							JSONObject product = sap.nextProduct();
							c2s.setProductToUploadOnBuffer(product, strategy);
						}
					} else {
						sap.setProductsStreamCursor(offsetdate);
						while(sap.hasMoreProducts()) {
							JSONObject product = sap.nextProduct();
							c2s.setProductToUploadOnBuffer(product, strategy);
						}
					}
				}
				c2s.setFinishProductUpload(strategy);
				offsetdate = c2s.getCursorOffset();
				entity = c2s.getEntity();
			}
			if(entity != null && entity.equals("orders")) {
				if(uploadOrders) {
					if(strategy != null && strategy.equals("pingsample")) {
						sap.setOrdersStreamCursor(offsetdate);
						if(sap.hasMoreOrders(offsetdate)) {
							JSONObject order = sap.nextOrder();
							c2s.setOrderToUploadOnBuffer(order, strategy);
						}
					} else {
						sap.setOrdersStreamCursor(offsetdate);
						while(sap.hasMoreOrders(offsetdate)) {
							JSONObject order = sap.nextOrder();
							c2s.setOrderToUploadOnBuffer(order, strategy);
						}
					}
				}
				c2s.setFinishOrderUpload(strategy);
				offsetdate = c2s.getCursorOffset();
			}
			//when no more in SAP, send finish command
			c2s.setFinishUpload(strategy);
			upstreamstatus = c2s.getUpstreamStatus();
		}
		if(upstreamstatus != null && (upstreamstatus.equals("finished") || upstreamstatus.equals("finishing"))) {
			//if is is finished, wait until server finishes
			ServiceLogger.log("Upload status is finished... waiting for C2S to complete the overall process...");
		}else {
			//unknown upstreamstatus code it means we crashed, notify of strange behavior to server... report?
			ServiceLogger.error("Unknown upstreamstatus code="+upstreamstatus+". Corrupt connection metadata...");
		}
		
		pauseWork(true);
		
	}
	
	private void pullToRemote() throws C2SUnreachableException,NoSAPB1Exception, InterruptedException {

		boolean downloadProducts = true;
		boolean downloadOrders = true;
		ServiceLogger.log("Pulling from remote...");
		String upstreamstatus = c2s.getUpstreamStatus();
		String entity = c2s.getEntity();
		
		if(upstreamstatus.equals("waiting")) {
			//if is waiting, good, change to initialized
			c2s.setInitializeDownload();
			upstreamstatus = c2s.getUpstreamStatus();
			//(if webonline responds error, then notify of strange behavior to server)
		}
		if(upstreamstatus.equals("initialized")) {
			//if is initialized, check cursor
			//from cursor start upload
			//when packet uploaded, renew offsets
			if(entity.equals("products")) {
				if(downloadProducts) {
					while(c2s.hasMoreUpdatedProducts(0)) {
						JSONObject product = c2s.nextProduct();
						if(product != null) {
							String id = (String) product.get("sku");
							try {
								JSONObject productstored = sap.storeProduct(product);
								c2s.sendProductPullSuccessNotification(id, productstored, true, "");
							}catch(NoSAPB1Exception e) {
								c2s.sendProductPullSuccessNotification(id, null, false, e.getMessage());
							}							
						}
					}
				}
				c2s.setFinishProductDownload();
				entity = c2s.getEntity();
			}
			if(entity.equals("orders")) {
				if(downloadOrders) {
					while(c2s.hasMoreUpdatedOrders(0)) {
						JSONObject order = c2s.nextOrder();
						String id = (String) order.get("orderid");
						try {
							JSONObject orderstored = sap.storeOrder(order);
							c2s.sendOrderPullSuccessNotification(id, orderstored, true, "");
						}catch(NoSAPB1Exception e) {
							ServiceLogger.error(e);
							c2s.sendOrderPullSuccessNotification(id, null, false, e.getMessage());
						}
					}
				}
				c2s.setFinishOrderDownload();
			}
			//when no more in SAP, send finish command
			c2s.setFinishDownload();
			upstreamstatus = c2s.getUpstreamStatus();
		}
		if(upstreamstatus.equals("finished") || upstreamstatus.equals("finishing")) {
			//if is is finished, wait until server finishes
			ServiceLogger.log("Download status is finished... waiting for C2S to complete the overall process...");
		}else {
			//unknown upstreamstatus code it means we crashed, notify of strange behavior to server... report?
			ServiceLogger.error("Unknown upstreamstatus code="+upstreamstatus+". Corrupt connection metadata...");
		}
		
		pauseWork(true);
		
	}
	
	private void work() throws C2SUnreachableException,NoSAPB1Exception,C2SRCServiceException, InterruptedException {
		
		if(checkIfWeAreFetchingRemote()) {
			fetchRemote();
		}
		if(checkIfWeArePullingToRemote()) {
			pullToRemote();
		}
		
	}
	
	private static class ShutdownHook implements Runnable {
		
		public void run() {
			onStop();
		}
		
		private void onStop() {
			ServiceLogger.error("Ended at " + new Date());
			System.out.flush();
			System.out.close();
		}
		
	}

}