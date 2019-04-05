package com.click2sync.rc.srv.sapb1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


import nl.flotsam.xeger.Xeger;

public class C2SProxy {
	
	Main delegate;
	final String keyFileName = "private_key.json";
	String key;
	JSONArray productsbuffer;
	JSONArray ordersbuffer;
	
	public C2SProxy(Main main) {
		delegate = main;
		productsbuffer = new JSONArray();
		ordersbuffer = new JSONArray();
	}
	
	public void sense() throws C2SUnreachableException {
		
		ServiceLogger.log("Sensing environment for C2S reachability...");
		try {
			int statuscode = HTTPWrapper.ping("/", delegate.config);
			if(statuscode != 200) {
				throw new C2SUnreachableException("Unexpected response status code from Click2Sync... statuscode:"+statuscode);
			}
		} catch (IOException e) {
			throw new C2SUnreachableException("Could not connect to Click2Sync... statuscode:"+e.getMessage());
		}
		
	}
	
	public boolean checkIfFetchingRemote() throws C2SUnreachableException, C2SRCServiceException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/ping";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String connectionstatus = (String) response.get("connectionstatus");
		if(connectionstatus != null && connectionstatus.equals("fetchingremote")) {
			return true;
		}else {
			return false;
		}
		
	}
	
	public boolean checkIfPullingToRemote() throws C2SUnreachableException, C2SRCServiceException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/ping";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String connectionstatus = (String) response.get("connectionstatus");
		if(connectionstatus != null && connectionstatus.equals("pulling")) {
			return true;
		}else {
			return false;
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public String getPrivateKey() throws C2SUnreachableException {
		
		if(key == null || key.equals("")) {
			try {
				ServiceLogger.log("Loading key from filesystem...");
				FileInputStream keyfis = new FileInputStream(new File(keyFileName));
				String keyfiledata = IOUtils.toString(keyfis, "UTF-8");
				JSONParser parser = new JSONParser();
				JSONObject keyobj = (JSONObject) parser.parse(keyfiledata);
				key = (String) keyobj.get("key");
			} catch (FileNotFoundException e) {
				ServiceLogger.log("Private key not found, generating one...");
				Xeger generator = new Xeger("[A-Z0-9]{256}");
				key = generator.generate();
				JSONObject keyobj = new JSONObject();
				keyobj.put("key", key);
				File file = new File(keyFileName);
				try {
					FileUtils.writeStringToFile(file, keyobj.toJSONString(), "UTF-8");
				} catch (IOException e1) {
					throw new C2SUnreachableException("Could not create C2S keyfile: "+e.getMessage());
				}
			} catch (IOException e) {
				throw new C2SUnreachableException("Could not read C2S keyfile data: "+e.getMessage());
			} catch (ParseException e) {
				throw new C2SUnreachableException("Could not parse C2S keyfile data: "+e.getMessage());
			}
		}
		return key;
		
	}
	
	public String getStatus() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/status";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String value = (String) response.get("status");
		return value;
		
	}
	
	public String getStrategy() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/strategy";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String value = (String) response.get("strategy");
		return value;
		
	}
	
	public String getUpstreamStatus() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/upstreamstatus";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String value = (String) response.get("upstreamstatus");
		return value;
		
	}
	
	public String getEntity() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/entity";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String value = (String) response.get("entity");
		return value;
		
	}
	
	public String getCursorOffset() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/cursoroffset";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String value = ""+response.get("cursoroffset");
		return value;
		
	}
	
	public JSONObject setInitializeUpload(String strategy) throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/initialize";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public JSONObject setInitializeDownload() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/initialize";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public JSONObject setFinishUpload(String strategy) throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public JSONObject setFinishDownload() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public boolean hasMoreUpdatedProducts(int attempts) throws C2SUnreachableException, InterruptedException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/hasmore";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String hasmorestatus = (String) response.get("hasmore");
		if(hasmorestatus != null && hasmorestatus.equals("ready")) {
			return true;
		}else if(hasmorestatus != null && hasmorestatus.equals("waiting")) {
			delegate.pauseWork(false);
			if(attempts > 10) {
				return false;
			}else {
				return hasMoreUpdatedProducts(attempts+1);
			}
		}else{
			return false;
		}
		
	}
	
	public JSONObject nextProduct() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/next";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		JSONObject value = (JSONObject) response.get("product");
		return value;
		
	}
	
	public boolean pullProductTransactionCompleted() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/readyfornext";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		boolean value = (boolean) response.get("ready");
		return value;
		
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject sendProductPullSuccessNotification(String id, JSONObject product, boolean succeded, String error) throws C2SUnreachableException, InterruptedException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/"+id+"/succeded";
		if(!succeded) {
			url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/"+id+"/failed";
		}
		JSONObject page = new JSONObject();
		if(succeded) {
			page.put("product", product);
		}else {
			page.put("error", "could not pull product");
			JSONArray reasons = new JSONArray();
			JSONObject reason = new JSONObject();
			reason.put("message", error);
			reason.put("code", 400);
			reasons.add(reason);
			page.put("reasons", reasons);
		}
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		delegate.pauseWork(false);
		while(!pullProductTransactionCompleted()) {
			delegate.pauseWork(false);
		}
		return response;
		
	}
	
	public JSONObject setFinishProductDownload() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/products/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public boolean hasMoreUpdatedOrders(int attempts) throws C2SUnreachableException, InterruptedException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/hasmore";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		String hasmorestatus = (String) response.get("hasmore");
		if(hasmorestatus != null && hasmorestatus.equals("ready")) {
			return true;
		}else if(hasmorestatus != null && hasmorestatus.equals("waiting")) {
			delegate.pauseWork(false);
			if(attempts > 10) {
				return false;
			}else {
				return hasMoreUpdatedOrders(attempts+1);
			}
		}else{
			return false;
		}
		
	}
	
	public JSONObject nextOrder() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/next";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		JSONObject value = (JSONObject) response.get("order");
		return value;
		
	}
	
	public boolean pullOrderTransactionCompleted() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/readyfornext";
		JSONObject response = HTTPWrapper.get(url, delegate.config);
		boolean value = (boolean) response.get("ready");
		return value;
		
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject sendOrderPullSuccessNotification(String id, JSONObject order, boolean succeded, String error) throws C2SUnreachableException, InterruptedException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/"+id+"/succeded";
		if(!succeded) {
			url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/"+id+"/failed";
		}
		JSONObject page = new JSONObject();
		if(succeded) {
			page.put("order", order);
		}else {
			page.put("error", "could not pull order");
			JSONArray reasons = new JSONArray();
			JSONObject reason = new JSONObject();
			reason.put("message", error);
			reason.put("code", 400);
			reasons.add(reason);
			page.put("reasons", reasons);
		}
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		delegate.pauseWork(false);
		while(!pullOrderTransactionCompleted()) {
			delegate.pauseWork(false);
		}
		return response;
		
	}
	
	public JSONObject setFinishOrderDownload() throws C2SUnreachableException {
		
		String key = getPrivateKey();
		String url = "/api/adapters/custom/reverse/connection/"+key+"/pull/orders/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject flushProductsToUpstream(String strategy) throws C2SUnreachableException {
		
		JSONObject response = new JSONObject();
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/products";
		JSONObject page = new JSONObject();
		page.put("products", productsbuffer);
		response = HTTPWrapper.post(url, page, delegate.config);
		productsbuffer.clear();
		return response;
		
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject flushOrdersToUpstream(String strategy) throws C2SUnreachableException {
		
		JSONObject response = new JSONObject();
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/orders";
		JSONObject page = new JSONObject();
		page.put("orders", ordersbuffer);
		response = HTTPWrapper.post(url, page, delegate.config);
		ordersbuffer.clear();
		return response;
		
	}
	
	@SuppressWarnings("unchecked")
	public void setProductToUploadOnBuffer(JSONObject product, String strategy) throws C2SUnreachableException {
		
		productsbuffer.add(product);
		int pagesize = Integer.parseInt(delegate.config.getProperty("c2sproductpagesize"));
		if(productsbuffer.size() >= pagesize) {
			flushProductsToUpstream(strategy);
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public void setOrderToUploadOnBuffer(JSONObject order, String strategy) throws C2SUnreachableException {
		
		ordersbuffer.add(order);
		int pagesize = Integer.parseInt(delegate.config.getProperty("c2sorderpagesize"));
		if(ordersbuffer.size() >= pagesize) {
			flushOrdersToUpstream(strategy);
		}
		
	}
	
	public JSONObject setFinishProductUpload(String strategy) throws C2SUnreachableException {
		
		flushProductsToUpstream(strategy);
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/products/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}
	
	public JSONObject setFinishOrderUpload(String strategy) throws C2SUnreachableException {
		
		flushOrdersToUpstream(strategy);
		String key = getPrivateKey();
		String verb = strategy.equals("pingsample") ? "pingsample" : "push";
		String url = "/api/adapters/custom/reverse/connection/"+key+"/"+verb+"/orders/finish";
		JSONObject page = new JSONObject();
		JSONObject response = HTTPWrapper.post(url, page, delegate.config);
		return response;
		
	}

}
