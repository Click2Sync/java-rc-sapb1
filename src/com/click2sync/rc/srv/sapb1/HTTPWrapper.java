package com.click2sync.rc.srv.sapb1;

import java.io.IOException;
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HTTPWrapper {
	
	public static int ping(String path, Properties config) throws IOException {
		
		String url = ""+config.getProperty("c2shostnameprefix")+path;
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(url);
		CloseableHttpResponse response1 = httpclient.execute(httpGet);
		StatusLine sl = response1.getStatusLine();
		int statuscode = sl.getStatusCode();
		return statuscode;
		
	}
	
	public static JSONObject get(String path, Properties config) throws C2SUnreachableException {

		String url = ""+config.getProperty("c2shostnameprefix")+path;
		int statuscode = -1;
		
		try {
			
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpGet httpGet = new HttpGet(url);
			CloseableHttpResponse response1 = httpclient.execute(httpGet);
			
			StatusLine sl = response1.getStatusLine();
			statuscode = sl.getStatusCode();
			
			HttpEntity entity = response1.getEntity();
			String responseString = EntityUtils.toString(entity, "UTF-8");
			JSONParser parser = new JSONParser();
			
			JSONObject response = (JSONObject) parser.parse(responseString);
			ServiceLogger.log("GET "+url+" "+statuscode);
			return response;
			
		} catch (IOException e) {
			
			ServiceLogger.log("GET "+url+" "+statuscode);
			throw new C2SUnreachableException("Could not establish connection GET on Click2Sync, from url '"+url+"', statuscode: '"+statuscode+"'... "+e.getMessage());
			
		} catch (ParseException e) {
			
			ServiceLogger.log("GET "+url+" "+statuscode);
			throw new C2SUnreachableException("Could not parser response of GET on Click2Sync, from url '"+url+"', statuscode: '"+statuscode+"'... "+e.getMessage());
			
		}
		
	}
	
	public static JSONObject post(String path, JSONObject body, Properties config) throws C2SUnreachableException {
		ServiceLogger.log("entered POST ");
		String url = ""+config.getProperty("c2shostnameprefix")+path;
		int statuscode = -1;
		
		try {
			
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(url);
			StringEntity reqBody = new StringEntity(body.toJSONString());
			httpPost.setHeader("Content-type", "application/json");
			httpPost.setEntity(reqBody);
			
			CloseableHttpResponse response1 = httpclient.execute(httpPost);
			
			StatusLine sl = response1.getStatusLine();
			statuscode = sl.getStatusCode();
			
			HttpEntity entity = response1.getEntity();
			String responseString = EntityUtils.toString(entity, "UTF-8");
			JSONParser parser = new JSONParser();
			
			JSONObject response = (JSONObject) parser.parse(responseString);
			ServiceLogger.log("POST "+url+" "+statuscode);
			return response;
			
		} catch (IOException e) {
			
			ServiceLogger.log("POST "+url+" "+statuscode);
			throw new C2SUnreachableException("Could not establish connection POST on Click2Sync, from url '"+url+"', statuscode: '"+statuscode+"'... "+e.getMessage());
			
		} catch (ParseException e) {
			
			ServiceLogger.log("POST "+url+" "+statuscode);
			throw new C2SUnreachableException("Could not parser response of POST on Click2Sync, from url '"+url+"', statuscode: '"+statuscode+"'... "+e.getMessage());
			
		}
		
	}

}
