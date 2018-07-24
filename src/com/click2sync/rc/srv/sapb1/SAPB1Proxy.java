package com.click2sync.rc.srv.sapb1;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import com.sap.smb.sbo.api.BusinessPartners;
import com.sap.smb.sbo.api.Company;
import com.sap.smb.sbo.api.Documents;
import com.sap.smb.sbo.api.IDocument_Lines;
import com.sap.smb.sbo.api.IDocuments;
import com.sap.smb.sbo.api.IItemWarehouseInfo;
import com.sap.smb.sbo.api.IItems_Prices;
import com.sap.smb.sbo.api.Items;
import com.sap.smb.sbo.api.Manufacturers;
import com.sap.smb.sbo.api.Recordset;
import com.sap.smb.sbo.api.SBOCOMConstants;
import com.sap.smb.sbo.api.SBOCOMException;
import com.sap.smb.sbo.api.SBOCOMUtil;

public class SAPB1Proxy {

	static final Pattern VALID_EMAIL_ADDRESS_REGEX = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);
	
	Main delegate;
	Company myCompany;
	Items products;
	Recordset productsrecordset;
	Documents orders;
	Recordset ordersrecordset;
	Manufacturers manufacturers;
	BusinessPartners customers;
	Recordset customersrecordset;
	
	Object manufacturersObj;
	Object ordersObj;
	Object customersObj;
	
	
	public int offsetproducts = 0;
	public int offsetorders = 0;
	
	public SAPB1Proxy(Main main) {
		delegate = main;
	}
	
	private void connect() throws NoSAPB1Exception {

		try {
			myCompany = (Company) SBOCOMUtil.newCompany();
			//SAP stuff
			myCompany.setServer(delegate.config.getProperty("server"));
			myCompany.setLicenseServer(delegate.config.getProperty("licenseserver"));
			myCompany.setUserName(delegate.config.getProperty("username"));
			myCompany.setPassword(delegate.config.getProperty("password"));
			myCompany.setLanguage(Integer.parseInt(delegate.config.getProperty("language_constant")));
			
			//DB stuff
			myCompany.setCompanyDB(delegate.config.getProperty("companydb"));
			myCompany.setDbServerType(Integer.parseInt(delegate.config.getProperty("dbservertype_constant")));
			myCompany.setUseTrusted(Boolean.parseBoolean(delegate.config.getProperty("dbauthwin")));
			myCompany.setDbUserName(delegate.config.getProperty("dbusername"));
			myCompany.setDbPassword(delegate.config.getProperty("dbpassword"));
			
		} finally {
			
			int result = myCompany.connect();
			
			if(result != 0) {
				
				String error = myCompany.getLastErrorDescription();
				int errorcode = myCompany.getLastErrorCode();
				throw new NoSAPB1Exception("Could not connect to SAP... errorcode:"+errorcode+" result:"+result+" description:"+error);
				
			}else {
				
				Object itemsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oItems);
				products = new Items(itemsObj);
				
				manufacturersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oManufacturers);
				manufacturers = new Manufacturers(manufacturersObj);
				
				ordersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_Document_oOrders);
				orders = new Documents(ordersObj);
				
				customersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oBusinessPartners);
				customers = new BusinessPartners(customersObj);
				
			}
			
		}
		
	}
	
	public void sense() throws NoSAPB1Exception {
		
		ServiceLogger.log("Sensing environment for SAP...");
		connect();
		
	}
	
	public void setProductsStreamCursor(Long offset) throws NoSAPB1Exception {
		
		Date date = new Date(offset);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String dateFormatted = sdf.format(date);
		Object itemsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oItems);
		products = new Items(itemsObj);
		Object rsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
		productsrecordset = new Recordset(rsObj);
		String query = "SELECT * FROM dbo.OITM WHERE UpdateDate >= ? ORDER BY UpdateDate ASC";
		String overridequery = delegate.config.getProperty("filterquerysqloverride");
		if(overridequery != null && overridequery.length() > 0) {
			query = overridequery;
		}
		query = query.replace("?", "'"+dateFormatted+"'");
		productsrecordset.doQuery(query);
		products.getBrowser().setRecordset(productsrecordset);
		
		
	}
	
	public boolean hasMoreProducts() throws NoSAPB1Exception {
		
		return !products.getBrowser().isEoF();
		
	}
	
	public JSONObject nextProduct() throws NoSAPB1Exception {
		
		JSONObject product = convertSAPProductToC2SProduct(products);
		products.getBrowser().moveNext();
		if(!productsrecordset.isEoF()) {
			productsrecordset.moveNext();
		}
		return product;
		
	}
	
	public void setOrdersStreamCursor(Long offsetdate) throws NoSAPB1Exception {
		
		Date date = new Date(offsetdate);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String dateFormatted = sdf.format(date);
		
		Object rsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
		ordersrecordset = new Recordset(rsObj);
		ordersrecordset.doQuery("SELECT * FROM dbo.ORDR WHERE UpdateDate >='"+dateFormatted+"'ORDER BY UpdateDate DESC OFFSET "+offsetorders+" ROWS FETCH NEXT 1000 ROWS ONLY");
		if(ordersrecordset.getRecordCount() > 0) {
			orders.getBrowser().release();
			orders.getBrowser().setRecordset(ordersrecordset);
		}

	}
	
	public boolean hasMoreOrders(Long offsetdate) throws NoSAPB1Exception {
		if(orders.getBrowser().isEoF()) {
			offsetorders = offsetorders+1000;
			ordersrecordset.release();
			this.setOrdersStreamCursor(offsetdate);
			if(ordersrecordset.getRecordCount() > 0) {
				//there are stills orders keep processing
				return true;
			} else {
				//there are no more orders
				return false;
			}
		} else {
			return true;
		}

	}
	
	public JSONObject nextOrder() throws NoSAPB1Exception {
		
		JSONObject order = convertSAPOrderToC2SOrder(orders);
		orders.getBrowser().moveNext();
		return order;
		
	}
	
	public JSONObject storeProduct(JSONObject product) throws NoSAPB1Exception {
		
		String productid = (String) product.get("_id");
		boolean isInsert = false;
		boolean found = products.getByKey(productid);
		if(!found) {
			isInsert = true;
		}
		if(isInsert) {
			throw new NoSAPB1Exception("SAPB1 is protected, the implementation restricts new products on SAP... ");
		} else {
			throw new NoSAPB1Exception("SAPB1 is protected, the implementation restricts product updates on SAP... ");
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject storeOrder(JSONObject order) throws NoSAPB1Exception {
		
		//extract data from external order object
		String orderid = (String) order.get("_id");
		
		boolean isInsert = false;
		int orderdockey = -1;
		try {
			orderdockey = Integer.parseInt(orderid);
			boolean found = orders.getByKey(orderdockey);
			if(!found) {
				isInsert = true;
			}
		}catch(NumberFormatException e) {//we have something like tmp-ordidblablabla
			isInsert = true;
		}
		
		if(isInsert) {
			
			//start building store request to SAPB1
			try {
				
				String orderconnectiontype = (String) order.toJSONString();
				ServiceLogger.log("connectiontype: " + orderconnectiontype);
				JSONArray orderItems = (JSONArray) order.get("orderItems");
				JSONObject buyer = (JSONObject) order.get("buyer");
				ServiceLogger.log("buyer: " + ((String) buyer.toJSONString()));
				String email = ""+buyer.get("email");
				String buyerid = ""+buyer.get("id");
				String refidstr = "";
				try {
					JSONArray referenceids = (JSONArray) order.get("otherids");
					JSONObject referenceid = (JSONObject) referenceids.get(0);
					refidstr = (String) referenceid.get("id");
					String conntype = (String) referenceid.get("connectiontype");
					String sufix = returnSufixForRefId(conntype);
					refidstr = sufix + refidstr;
				}catch(Exception e) {
					ServiceLogger.error(new C2SRCServiceException("Could not extract referenceid from online order. "+e.getMessage()));
				}
				
				IDocuments oDocs = SBOCOMUtil.newDocuments(myCompany, SBOCOMConstants.BoObjectTypes_Document_oOrders);
				
				if(isValidEmail(email)) {
					Object rsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
					customersrecordset = new Recordset(rsObj);
					customersrecordset.doQuery("SELECT * FROM dbo.OCRD WHERE [E_Mail] = '"+email+"'");
					try {
						//if(cardcode.length() > 0) {//found
						if(customersrecordset.getRecordCount() > 0) {
							customers.getBrowser().setRecordset(customersrecordset);
							String cardcode = customers.getCardCode();
							oDocs.setCardCode(cardcode);
						}else {//not found
							
							try {//search if id no.2 is found this can only be true if other order
								customersrecordset = new Recordset(rsObj);
								customersrecordset.doQuery("SELECT * FROM dbo.OCRD WHERE [AddID] = '"+buyerid+"'");
								if(customersrecordset.getRecordCount() > 0) {
									customers.getBrowser().setRecordset(customersrecordset);
									String cardcode = customers.getCardCode();
									oDocs.setCardCode(cardcode);
								} else {//create a new business partner
									String autogen = delegate.config.getProperty("autogeneratebusinesspartners");
									if(autogen != null && autogen.equals("true")) {
										String classnamebpg = delegate.config.getProperty("classnameforbusinesspartnergeneration");
										BusinessPartnerAutoGeneration bpagobj = new BusinessPartnerAutoGeneration();
										if(classnamebpg != null && classnamebpg.length() > 0){
											Class<?> clazz = Class.forName(classnamebpg);
											Object nakedobj = clazz.newInstance();
											if(nakedobj instanceof BusinessPartnerAutoGeneration) {
												bpagobj = (BusinessPartnerAutoGeneration)nakedobj;
											}
										}
										JSONObject bpinput = new JSONObject();
										bpinput.put("order", order);
										String cardcode = bpagobj.generateBusinessPartner(myCompany, delegate.config, bpinput);
										oDocs.setCardCode(cardcode);
									} else {
										oDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
									}
								}
							} catch(Exception e) {
								ServiceLogger.error(new C2SRCServiceException("Could not find existing ID No. 2 in SAP. "+e.getMessage()));
								String autogen = delegate.config.getProperty("autogeneratebusinesspartners");
								if(autogen != null && autogen.equals("true")) {
									String classnamebpg = delegate.config.getProperty("classnameforbusinesspartnergeneration");
									BusinessPartnerAutoGeneration bpagobj = new BusinessPartnerAutoGeneration();
									if(classnamebpg != null && classnamebpg.length() > 0){
										Class<?> clazz = Class.forName(classnamebpg);
										Object nakedobj = clazz.newInstance();
										if(nakedobj instanceof BusinessPartnerAutoGeneration) {
											bpagobj = (BusinessPartnerAutoGeneration)nakedobj;
										}
									}
									JSONObject bpinput = new JSONObject();
									bpinput.put("order", order);
									String cardcode = bpagobj.generateBusinessPartner(myCompany, delegate.config, bpinput);
									oDocs.setCardCode(cardcode);
								} else {
									oDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
								}
							}
						}
					}catch(Exception e) {
						ServiceLogger.error(new C2SRCServiceException("Could not find existing e-mail in SAP. "+e.getMessage()));
						oDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
					}
				}else {
					oDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
				}
				
				oDocs.setDocDate(new Date());
				oDocs.setDocDueDate(new Date());
				oDocs.setDocType(SBOCOMConstants.BoDocumentTypes_dDocument_Items);
				oDocs.setNumAtCard(refidstr);
				if(delegate.config.getProperty("defaultsosalespersoncode").length() > 0) {
					try {
						oDocs.setSalesPersonCode(Integer.parseInt(delegate.config.getProperty("defaultsosalespersoncode")));
					}catch(Exception e) {
						ServiceLogger.error(new C2SRCServiceException("Could not set sales person code on order. "+e.getMessage()));
					}
				}
				
				IDocument_Lines lines = oDocs.getLines();
				for (int i=0; i<orderItems.size(); i++) {
					JSONObject orderItem = (JSONObject) orderItems.get(i);
					String itemcode = (String) orderItem.get("id");
					lines.add();
					lines.setCurrentLine(i);
					if(itemcode.equals("shipping") && !delegate.config.getProperty("shippingitemcode").equals("")) {
						lines.setItemCode(delegate.config.getProperty("shippingitemcode"));
						lines.setQuantity(1d);
						lines.setUnitPrice(convertToDouble(orderItem.get("unitPrice"))/(1.16));
					}else if(itemcode.equals("surcharge") && !delegate.config.getProperty("surchargeitemcode").equals("")) {
						lines.setItemCode(delegate.config.getProperty("surchargeitemcode"));
						lines.setQuantity(1d);
						System.out.println(orderItem.get("unitPrice"));
						System.out.println(convertToDouble(orderItem.get("unitPrice")));
						System.out.println(convertToDouble(orderItem.get("unitPrice"))/(1.16));
						lines.setUnitPrice(convertToDouble(orderItem.get("unitPrice"))/(1.16));
					}else {
						Double quantity = (double) ((Long)orderItem.get("quantity")).longValue();
						lines.setItemCode(itemcode);
						lines.setQuantity(quantity);
						
						//force last_update refresh
						Object itemsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oItems);
						products = new Items(itemsObj);
						Items thisprod = new Items(itemsObj);
						thisprod.getByKey(itemcode);
						thisprod.update();
					}
				}
				
				int docadded = oDocs.add();
				if(docadded != 0) {
					String error = myCompany.getLastErrorDescription();
					int errorcode = myCompany.getLastErrorCode();
					throw new NoSAPB1Exception("Could not store document... errorcode:"+errorcode+" result:"+docadded+" description:"+error);
				}else {
					String insertedkey = myCompany.getNewObjectCode();
					orders.getByKey(Integer.parseInt(insertedkey));
					return convertSAPOrderToC2SOrder(orders);
				}
			} catch (SBOCOMException e) {
				String error = myCompany.getLastErrorDescription();
				int errorcode = myCompany.getLastErrorCode();
				throw new NoSAPB1Exception("Could not create document... errorcode:"+errorcode+" description:"+error);
			}
			
		} else {//is update
			
			orders.getByKey(orderdockey);
			return convertSAPOrderToC2SOrder(orders);
			
		}
		
		
	}
	
	static String returnSufixForRefId(String connectiontype) {
		String sufix = "";
		switch (connectiontype) {
			case "mercadolibre":
				sufix = "ML";
				break;
			case "prestashop":
				sufix = "PS";
				break;
			case "woocommerce":
				sufix = "WC";
				break;
			default:
				sufix = "";
				break;
		}
		return sufix;
	}
	
	static double convertToDouble(Object longValue){
		double valueTwo = 0; // whatever to state invalid!
		if(longValue instanceof Long) {
			valueTwo = ((Long) longValue).doubleValue();
		}else if(longValue instanceof Float) {
			valueTwo = ((Float) longValue).doubleValue();
		}else if(longValue instanceof Double) {
			valueTwo = ((Double) longValue).doubleValue();
		}else {
			try {
				valueTwo = (double)valueTwo;
			}catch(Exception e) {
				System.out.println("could not find and cast conversion. longValue is instance of "+longValue.getClass());
			}
		}
		return valueTwo;
	}
	
	public static boolean isValidEmail(String emailStr) {
	        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX .matcher(emailStr);
	        return matcher.find();
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject convertSAPProductToC2SProduct(Items items) {		
		JSONObject product = new JSONObject();
		product.put("_id",products.getItemCode());
		product.put("sku",products.getItemCode());
		product.put("title",products.getItemName());
		product.put("url","");
		if(manufacturers.getByKey(products.getManufacturer())) {
			product.put("brand",manufacturers.getManufacturerName());
		} else {
			product.put("brand","");
		}
		product.put("mpn","");
		product.put("model","");
		product.put("description","");
		
		for(int i=0; i<productsrecordset.getFields().getCount(); i++) {
			try {
				if(productsrecordset.getFields().item(i).getName().equals("UpdateDate")) {
					Date updatedate = (Date) productsrecordset.getFields().item(i).getValue();
					product.put("last_updated", updatedate.getTime());
					break;
				}
			}catch(Exception e) {}
		}
		
		JSONArray variations = new JSONArray();
		product.put("variations", variations);
		JSONObject variation = new JSONObject();
		variations.add(variation);
		
		JSONArray availabilities = new JSONArray();
		variation.put("availabilities", availabilities);
		
		if(!delegate.config.getProperty("warehousestoaggregate").equals("")) {
			String warehousestoaggregate = delegate.config.getProperty("warehousestoaggregate");
			String[] subsetwarehousecodes = warehousestoaggregate.split(",");
			IItemWarehouseInfo whsinfo = products.getWhsInfo();
			for(int i=0; i<whsinfo.getCount(); i++) {
				whsinfo.setCurrentLine(i);
				for(int j=0; j<subsetwarehousecodes.length; j++) {
					String subsetwarehousecode = subsetwarehousecodes[j];
					if(subsetwarehousecode.trim().equals(whsinfo.getWarehouseCode().trim())) {
						JSONObject availability = new JSONObject();
						availabilities.add(availability);
						availability.put("tag",""+subsetwarehousecode);
						availability.put("quantity",(int)whsinfo.getInStock().doubleValue()+(int)whsinfo.getOrdered().doubleValue()-(int)whsinfo.getCommitted().doubleValue());
					}
				}
			}
		} else {
			JSONObject availability = new JSONObject();
			availabilities.add(availability);
			availability.put("tag","default");
			availability.put("quantity",products.getQuantityOnStock());
		}

		JSONArray prices = new JSONArray();
		variation.put("prices",prices);
		JSONObject price = new JSONObject();
		prices.add(price);
		price.put("tag", "default");
		price.put("currency", delegate.config.getProperty("defaultcurrency"));
		String pricelistoverride = delegate.config.getProperty("pricelistoverride");
		double priceval = products.getPriceList().getPrice();
		if(pricelistoverride != null && pricelistoverride.length() > 0) {
			IItems_Prices pricelists = products.getPriceList();
			for(int i=0; i<pricelists.getCount(); i+=1) {
				pricelists.setCurrentLine(i);
				if((""+pricelists.getPriceList()).equals(pricelistoverride)) {
					priceval = pricelists.getPrice();
				}
			}
		}
		price.put("number", priceval);
		
		String imgurl = "";
		String picurl = products.getPicture();
		JSONArray images = new JSONArray();
		variation.put("images",images);
		
		if(picurl.length()>0) {
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			try {
				BufferedImage image = ImageIO.read(new File(delegate.config.getProperty("picturesfolder")+picurl));
			    ImageIO.write(image, "png", baos);
			} catch (IOException e) {
			    ServiceLogger.error(new C2SRCServiceException("Could not read supposed image from SAP. "+e.getMessage()));
			}
			String bytesdrawed = DatatypeConverter.printBase64Binary(baos.toByteArray());
			if(bytesdrawed.length()>0) {
				imgurl = "data:image/png;base64," + bytesdrawed;
				JSONObject image = new JSONObject();
				images.add(image);
				image.put("url", imgurl);
			}
			
		}
		
		JSONArray videos = new JSONArray();
		variation.put("videos", videos);
		
		variation.put("barcode", products.getBarCode());
		variation.put("size", "");
		variation.put("color", "");
		
		return product;
		
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject convertSAPOrderToC2SOrder(Documents orders) {
		
		String orderid = ""+orders.getDocNum();
		String status = orders.getDocumentStatus().equals(SBOCOMConstants.BoSoStatus_so_Open) ? "open" : (orders.getDocumentStatus().equals(SBOCOMConstants.BoSoStatus_so_Closed) ? "closed" : "unknown");
		Long dateCreated = orders.getCreationDate().getTime();
		Long dateClosed = orders.getClosingDate().getTime();
		dateCreated = dateCreated > 0 ? dateCreated : 0;
		dateClosed = dateClosed > 0 ? dateClosed : 0;
		Double total_amount = orders.getDocTotal();
		JSONObject total = new JSONObject();
		total.put("amount", total_amount);
		total.put("currency", delegate.config.getProperty("defaultcurrency"));
		String buyer_id = orders.getCardCode();
		
		ServiceLogger.log("About to getByKey() of: "+orders.getCardCode());

		Object rsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
		customersrecordset = new Recordset(rsObj);
		customersrecordset.doQuery("SELECT * FROM dbo.OCRD WHERE [CardCode] = '"+orders.getCardCode()+"'");
		customers.getBrowser().setRecordset(customersrecordset);
		
		
		
		
		//customers.getByKey(orders.getCardCode());
		String buyer_email = customers.getEmailAddress();
		String buyer_phone = customers.getPhone1();
		if(buyer_phone.equals("")) {
			buyer_phone = customers.getPhone2();
			if(buyer_phone.equals("")) {
				if(buyer_phone.equals("")) {
					buyer_phone = customers.getContactEmployees().getPhone1();
					if(buyer_phone.equals("")) {
						buyer_phone = customers.getContactEmployees().getPhone2();
						if(buyer_phone.equals("")) {
							buyer_phone = customers.getContactEmployees().getMobilePhone();
						}
					}
				}
			}
		}
		String buyer_firstName = customers.getContactEmployees().getFirstName();
		if(buyer_firstName.equals("")) {
			buyer_firstName = customers.getContactEmployees().getName();
		}
		String buyer_lastName = customers.getContactEmployees().getLastName();
		if(buyer_lastName.equals("")) {
			buyer_lastName = customers.getContactPerson();
		}
		JSONObject buyer = new JSONObject();
		buyer.put("id", buyer_id);
		buyer.put("email", buyer_email);
		buyer.put("phone", buyer_phone);
		buyer.put("firstName", buyer_firstName);
		buyer.put("lastName", buyer_lastName);
		
		JSONArray orderItems = new JSONArray();
		for(int i=0; i<orders.getLines().getCount(); i++) {
			JSONObject orderItem = new JSONObject();
			orders.getLines().setCurrentLine(i);
			String itemcode = orders.getLines().getItemCode();
			if(!delegate.config.getProperty("shippingitemcode").equals("") && itemcode.equals(delegate.config.getProperty("shippingitemcode"))) {
				orderItem.put("id", "shipping");
			}else if(!delegate.config.getProperty("surchargeitemcode").equals("") && itemcode.equals(delegate.config.getProperty("surchargeitemcode"))) {
				orderItem.put("id", "surcharge");
			}else {
				orderItem.put("id", itemcode);
			}
			orderItem.put("variation_id", "");
			orderItem.put("quantity", orders.getLines().getQuantity());
			orderItem.put("unitPrice", orders.getLines().getUnitPrice());
			orderItem.put("currencyId", delegate.config.getProperty("defaultcurrency"));
			orderItems.add(orderItem);
		}
		
		JSONObject order = new JSONObject();
		order.put("_id", orderid);
		order.put("orderid", orderid);
		order.put("status", status);
		order.put("dateCreated", dateCreated);
		order.put("dateClosed", dateClosed);
		order.put("total", total);
		order.put("buyer", buyer);
		order.put("orderItems", orderItems);
		
		if(ordersrecordset != null && !ordersrecordset.isEoF()) {
			for(int i=0; i<ordersrecordset.getFields().getCount(); i++) {
				try {
					if(ordersrecordset.getFields().item(i).getName().equals("UpdateDate")) {
						Date updatedate = (Date) ordersrecordset.getFields().item(i).getValue();
						order.put("last_updated", updatedate.getTime());
						break;
					}
				}catch(Exception e) {}
			}
		}
		
		customersrecordset.release();
		customers.getBrowser().release();;
		
		return order;
		
	}

}