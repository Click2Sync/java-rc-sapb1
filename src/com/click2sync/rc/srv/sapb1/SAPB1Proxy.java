package com.click2sync.rc.srv.sapb1;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.sap.smb.sbo.api.BusinessPartners;
import com.sap.smb.sbo.api.Company;
import com.sap.smb.sbo.api.Documents;
import com.sap.smb.sbo.api.IContactEmployees;
import com.sap.smb.sbo.api.IDocument_Lines;
import com.sap.smb.sbo.api.IDocuments;
import com.sap.smb.sbo.api.IField;
import com.sap.smb.sbo.api.IFields;
import com.sap.smb.sbo.api.IItemBarCodes;
import com.sap.smb.sbo.api.IItemUnitOfMeasurements;
import com.sap.smb.sbo.api.IItemWarehouseInfo;
import com.sap.smb.sbo.api.IItems_Prices;
import com.sap.smb.sbo.api.IUnitOfMeasurementGroup;
import com.sap.smb.sbo.api.IUnitOfMeasurementGroupParams;
import com.sap.smb.sbo.api.IUnitOfMeasurementGroupParamsCollection;
import com.sap.smb.sbo.api.IUoMGroupDefinition;
import com.sap.smb.sbo.api.IUoMGroupDefinitionCollection;
import com.sap.smb.sbo.api.IUoMPrices;
import com.sap.smb.sbo.api.Items;
import com.sap.smb.sbo.api.Manufacturers;
import com.sap.smb.sbo.api.Recordset;
import com.sap.smb.sbo.api.SBOCOMConstants;
import com.sap.smb.sbo.api.SBOCOMException;
import com.sap.smb.sbo.api.SBOCOMUtil;
import com.sap.smb.sbo.api.UnitOfMeasurementGroupsService;
import com.sap.smb.sbo.api.UnitOfMeasurementsService;

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
	UnitOfMeasurementGroupsService uomgrpsvc;
	UnitOfMeasurementsService uomsvc;
	
	Object manufacturersObj;
	Object ordersObj;
	Object customersObj;
	Object unitsOfMeasureGroupsObj;
	Object unitsOfMeasureObj;
	
	JSONArray explodedprodsbuffer;
	
	Long offsetproductsdate = 0L;
	public int offsetproductscurr = 0;
	Long offsetordersdate = 0L;
	public int offsetorderscurr = 0;
	
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
			
		} catch(Exception e) {
			System.out.println(e);
		} finally {
			
			int result = myCompany.connect();
			if(result != 0) {
				
				String error = myCompany.getLastErrorDescription();
				int errorcode = myCompany.getLastErrorCode();
				throw new NoSAPB1Exception("Could not connect to SAP... errorcode:"+errorcode+" result:"+result+" description:"+error);
				
			}else {
				
				Object itemsObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oItems);
				products = new Items(itemsObj);
				Object rsObjProd = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
				productsrecordset = new Recordset(rsObjProd);
				
				manufacturersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oManufacturers);
				manufacturers = new Manufacturers(manufacturersObj);
				
				ordersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_Document_oOrders);
				orders = new Documents(ordersObj);
				Object rsObjOrd = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
				ordersrecordset = new Recordset(rsObjOrd);
				
				customersObj = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_oBusinessPartners);
				customers = new BusinessPartners(customersObj);
				Object rsObjCus = myCompany.getBusinessObject(SBOCOMConstants.BoObjectTypes_BoRecordset);
				customersrecordset = new Recordset(rsObjCus);

				unitsOfMeasureGroupsObj = myCompany.getCompanyService().getBusinessService(SBOCOMConstants.ServiceTypes_UnitOfMeasurementGroupsService);
				uomgrpsvc = new UnitOfMeasurementGroupsService(unitsOfMeasureGroupsObj);
				
				unitsOfMeasureObj = myCompany.getCompanyService().getBusinessService(SBOCOMConstants.ServiceTypes_UnitOfMeasurementsService);
				uomsvc = new UnitOfMeasurementsService(unitsOfMeasureObj);
				
			}
			
		}
		
	}
	
	public void sense() throws NoSAPB1Exception {
		
		ServiceLogger.log("Sensing environment for SAP...");
		connect();
		
	}
	
	public void setProductsStreamCursor(String offset) throws NoSAPB1Exception {
		
		String[] offsetsplit = offset.split(" - ");
		String offsetuse = "0";
		Date date = new Date(0);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String dateFormatted;
		if(!offset.equals("0") && offsetsplit.length > 1) {
			offsetuse = offsetsplit[1];
			offsetproductsdate = Long.parseLong(offsetsplit[0]);
			offsetproductscurr = Integer.parseInt(offsetsplit[1]);
			date = new Date(offsetproductsdate);
			dateFormatted = sdf.format(date);
		} else {
			offsetuse = offset;
			offsetproductsdate = Long.parseLong(offsetuse);
			offsetproductscurr = 0;
			date = new Date(offsetproductsdate);
			dateFormatted = sdf.format(date);
		}

		String query = "SELECT * FROM dbo.OITM WHERE UpdateDate >= ? ORDER BY UpdateDate ASC OFFSET "+(offsetproductscurr)+" ROWS FETCH NEXT 1000 ROWS ONLY";
		String overridequery = delegate.config.getProperty("filterquerysqloverride");
		if(overridequery != null && overridequery.length() > 0) {
			query = overridequery;
		}
		query = query.replace("?", "'"+dateFormatted+"'");
		query = query.replace("offsetproducts", ""+offsetproductscurr);
		
		ServiceLogger.log("cursor: "+offset);
		ServiceLogger.log("query products: "+query);
		productsrecordset.doQuery(query);
		ServiceLogger.log("resultsetcount:"+productsrecordset.getRecordCount()+" isEoF:"+productsrecordset.isEoF()+" isBoF:"+productsrecordset.isBoF());
		if(productsrecordset.getRecordCount() > 0 && !productsrecordset.isEoF()) {
			products.getBrowser().setRecordset(productsrecordset);
		}
		
	}
	
	public boolean hasMoreProducts(String offsetdate) throws NoSAPB1Exception {

		ServiceLogger.log("hasMoreProducts: "+offsetdate);
		ServiceLogger.log("hasMoreProductsCount:"+products.getBrowser().getRecordCount()+" isEoF:"+products.getBrowser().isEoF()+" isBoF:"+products.getBrowser().isBoF());
		if(explodedprodsbuffer != null && explodedprodsbuffer.size() > 0) {
			return true;
		} else {

			if(products.getBrowser().isEoF()) {
				
				String newoffset = offsetproductsdate+" - "+offsetproductscurr;
				this.setProductsStreamCursor(newoffset);
				if(productsrecordset.getRecordCount() == 0 || (productsrecordset.isBoF() && productsrecordset.isEoF())) {
					return false;
				}else {
					return true;
				}
				
			} else {
				
				return true;
				
			}

		}

	}
	
	public JSONObject nextProduct() throws NoSAPB1Exception {
		
		JSONObject product = null;
		if(explodedprodsbuffer == null || explodedprodsbuffer.size() == 0) {
			products.getBrowser().moveNext();
			explodedprodsbuffer = convertSAPProductToC2SProducts();
		}
		if(explodedprodsbuffer != null && explodedprodsbuffer.size() > 0) {
			product = (JSONObject) explodedprodsbuffer.remove(0);
		}
		return product;
		
	}
	
	public void setOrdersStreamCursor(String offset) throws NoSAPB1Exception {
		
		String[] offsetsplit = offset.split(" - ");
		String offsetuse = "0";
		Date date = new Date(0);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String dateFormatted;
		
		if(!offset.equals("0") && offsetsplit.length > 1) {
			offsetuse = offsetsplit[1];
			offsetordersdate = Long.parseLong(offsetsplit[0]);
			offsetorderscurr = Integer.parseInt(offsetsplit[1]);
			date = new Date(offsetordersdate);
			dateFormatted = sdf.format(date);
		} else {
			offsetuse = offset;
			offsetordersdate = Long.parseLong(offsetuse);
			offsetorderscurr = 0;
			date = new Date(offsetordersdate);
			dateFormatted = sdf.format(date);
		}
		
		String query = "SELECT * FROM dbo.ORDR WHERE UpdateDate >='"+dateFormatted+"' ORDER BY DocNum DESC OFFSET "+(offsetorderscurr)+" ROWS FETCH NEXT 1000 ROWS ONLY";
		String overridequery = delegate.config.getProperty("filterquerysqloverrideorders");
		if(overridequery != null && overridequery.length() > 0) {
			query = overridequery;
		}
		query = query.replace("?", "'"+dateFormatted+"'");
		query = query.replace("offsetorders", ""+offsetorderscurr);
		
		ServiceLogger.log("cursor: "+offset);
		ServiceLogger.log("query orders: "+query);
		ordersrecordset.doQuery(query);
		ServiceLogger.log("resultsetcount:"+ordersrecordset.getRecordCount()+" isEoF:"+ordersrecordset.isEoF()+" isBoF:"+ordersrecordset.isBoF());
		if(ordersrecordset.getRecordCount() > 0 && !ordersrecordset.isEoF()) {
			orders.getBrowser().setRecordset(ordersrecordset);
		}

	}
	
	public boolean hasMoreOrders(String offsetdate) throws NoSAPB1Exception {
		
		ServiceLogger.log("hasMoreOrders: "+offsetdate);
		ServiceLogger.log("hasMoreOrdersCount:"+orders.getBrowser().getRecordCount()+" isEoF:"+orders.getBrowser().isEoF()+" isBoF:"+orders.getBrowser().isBoF());
		if(orders.getBrowser().isEoF()) {
			
			String newoffset = offsetordersdate+" - "+offsetorderscurr;
			this.setOrdersStreamCursor(newoffset);
			if(ordersrecordset.getRecordCount() == 0 || (ordersrecordset.isBoF() && ordersrecordset.isEoF())) {
				return false;
			}else {
				return true;
			}
			
		} else {
			return true;
		}
	}
	
	public JSONObject nextOrder() throws NoSAPB1Exception {
		
		JSONObject order = convertSAPOrderToC2SOrder();
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
	private void fillstuff(IDocuments xDocz, JSONObject order, String baseentrycode) throws SBOCOMException {
		
		JSONArray orderItems = (JSONArray) order.get("orderItems");
		JSONObject buyer = (JSONObject) order.get("buyer");
		String email = ""+buyer.get("email");
		String buyerid = ""+buyer.get("id");
		String refidstr = "";
		try {
			JSONArray referenceids = (JSONArray) order.get("otherids");
			JSONObject referenceid = (JSONObject) referenceids.get(0);
			refidstr = (String) referenceid.get("id");
			String conntype = (String) referenceid.get("connectiontype");
			if(delegate.config.getProperty("printsuffixofordersourcetype").equals("true")) {
				String sufix = returnSufixForRefId(conntype);
				refidstr = sufix + refidstr;						
			}
		}catch(Exception e) {
			ServiceLogger.error(new C2SRCServiceException("Could not extract referenceid from online order. "+e.getMessage()));
		}
		
		IDocuments xDocs = xDocz;
		
		if(isValidEmail(email)) {
			
			String query = "SELECT * FROM dbo.OCRD WHERE [E_Mail] = email";
			String overridedquery = delegate.config.getProperty("filterquerysqloverridebpsearchemail");
			if(overridedquery != null && overridedquery.length() > 0) {
				query = overridedquery;
			}
			query = query.replace("email", "'"+email+"'");
			customersrecordset.doQuery(query);
			try {
				
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
				
				if(customersrecordset.getRecordCount() > 0) {
					customers.getBrowser().setRecordset(customersrecordset);
					String cardcode = customers.getCardCode();
					xDocs.setCardCode(cardcode);
					//bpagobj.updateBusinessPartner(myCompany, delegate.config, bpinput, customers);
				}else {//not found
					
					try {//search if id no.2 is found this can only be true if other order
						String querybyid = "SELECT * FROM dbo.OCRD WHERE [AddID] = buyerid";
						String overridedquerybyid = delegate.config.getProperty("filterquerysqloverridebpsearchbyid");
						if(overridedquerybyid != null && overridedquerybyid.length() > 0) {
							querybyid = overridedquerybyid;
						}
						querybyid = querybyid.replace("buyerid", "'"+buyerid+"'");
						customersrecordset.doQuery(querybyid);
						if(customersrecordset.getRecordCount() > 0) {
							customers.getBrowser().setRecordset(customersrecordset);
							String cardcode = customers.getCardCode();
							xDocs.setCardCode(cardcode);
							//bpagobj.updateBusinessPartner(myCompany, delegate.config, bpinput, customers);
						} else {//create a new business partner
							String autogen = delegate.config.getProperty("autogeneratebusinesspartners");
							if(autogen != null && autogen.equals("true")) {
								String cardcode = bpagobj.generateBusinessPartner(myCompany, delegate.config, bpinput);
								xDocs.setCardCode(cardcode);
							} else {
								xDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
							}
						}
					} catch(Exception e) {
						//ServiceLogger.error(new C2SRCServiceException("Could not find existing ID No. 2 in SAP. "+e.getMessage()));
						ServiceLogger.error(new C2SRCServiceException("Could not find existing ID No. 2 in SAP. No match found"));
						String autogen = delegate.config.getProperty("autogeneratebusinesspartners");
						if(autogen != null && autogen.equals("true")) {
							String cardcode = bpagobj.generateBusinessPartner(myCompany, delegate.config, bpinput);
							xDocs.setCardCode(cardcode);
						} else {
							xDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
						}
					}
				}
			}catch(Exception e) {
				ServiceLogger.error(new C2SRCServiceException("Could not find existing e-mail in SAP. "+e.getMessage()));
				xDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
			}
		}else {
			xDocs.setCardCode(delegate.config.getProperty("defaultsobusinesspartnercardcode"));
		}
		
		xDocs.setDocDate(new Date());
		xDocs.setDocDueDate(new Date());
		xDocs.setDocType(SBOCOMConstants.BoDocumentTypes_dDocument_Items);
		xDocs.setNumAtCard(refidstr);
		try {
			xDocs.setSeries(Integer.parseInt(delegate.config.getProperty("defaultsoseries")));
		}catch(NumberFormatException e) {
			ServiceLogger.error(new C2SRCServiceException("Could not set series. "+e.getMessage()));
		}
		
		if(delegate.config.getProperty("defaultsosalespersoncode").length() > 0) {
			try {
				xDocs.setSalesPersonCode(Integer.parseInt(delegate.config.getProperty("defaultsosalespersoncode")));
			}catch(Exception e) {
				ServiceLogger.error(new C2SRCServiceException("Could not set sales person code on order. "+e.getMessage()));
			}
		}
		
		IDocument_Lines lines = xDocs.getLines();
		for (int i=0; i<orderItems.size(); i++) {
			
			JSONObject orderItem = (JSONObject) orderItems.get(i);
			String itemcode = (String) orderItem.get("id");
			
			lines.add();
			lines.setCurrentLine(i);
			
			if(!baseentrycode.equals("")) {
				int codeval = 0;
				try {
					codeval = Integer.parseInt(baseentrycode);
				}catch(Exception e) {
					ServiceLogger.error(new C2SRCServiceException("Could not convert relationship code for quotation->order. "+e.getMessage()));
				}
				if(codeval != 0) {
					lines.setBaseEntry(codeval);
					lines.setBaseLine(i);
					lines.setBaseType(SBOCOMConstants.BoObjectTypes_Document_oQuotations);
				}
			}else {
				if(itemcode.equals("shipping") && !delegate.config.getProperty("shippingitemcode").equals("")) {
					lines.setItemCode(delegate.config.getProperty("shippingitemcode"));
					lines.setQuantity(1d);
					lines.setUnitPrice(convertToDouble(orderItem.get("unitPrice"))/(1.16));
				}else if(itemcode.equals("surcharge") && !delegate.config.getProperty("surchargeitemcode").equals("")) {
					lines.setItemCode(delegate.config.getProperty("surchargeitemcode"));
					lines.setQuantity(1d);
					lines.setUnitPrice(convertToDouble(orderItem.get("unitPrice"))/(1.16));
				}else {
					
					Double quantity = (double) ((Long)orderItem.get("quantity")).longValue();
					lines.setItemCode(itemcode);
					lines.setQuantity(quantity);
					
					//force last_update refresh
					products.getByKey(itemcode);
					products.update();
				}
			}
			
		}
		
	}
	
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

			String generateorderquotation = delegate.config.getProperty("generateorderquotation");
			String baseentrycode = "";
			
			if(generateorderquotation != null && generateorderquotation.equals("true")) {
				//start building store request to SAPB1 but for orderoffer
				try {
					IDocuments qDocs = SBOCOMUtil.newDocuments(myCompany, SBOCOMConstants.BoObjectTypes_Document_oQuotations);
					fillstuff(qDocs, order, baseentrycode);
					int docadded = qDocs.add();
					if(docadded != 0) {
						String error = myCompany.getLastErrorDescription();
						int errorcode = myCompany.getLastErrorCode();
						throw new NoSAPB1Exception("Could not store document... errorcode:"+errorcode+" result:"+docadded+" description:"+error);
					}else {
						baseentrycode = myCompany.getNewObjectCode();
					}
					qDocs.release();
				} catch (SBOCOMException e) {
					String error = myCompany.getLastErrorDescription();
					int errorcode = myCompany.getLastErrorCode();
					throw new NoSAPB1Exception("Could not create document... errorcode:"+errorcode+" description:"+error);
				}
			}

			//start building store request to SAPB1
			try {
				IDocuments oDocs = SBOCOMUtil.newDocuments(myCompany, SBOCOMConstants.BoObjectTypes_Document_oOrders);
				fillstuff(oDocs, order, baseentrycode);
				
				int docadded = oDocs.add();
				if(docadded != 0) {
					String error = myCompany.getLastErrorDescription();
					int errorcode = myCompany.getLastErrorCode();
					oDocs.release();
					throw new NoSAPB1Exception("Could not store document... errorcode:"+errorcode+" result:"+docadded+" description:"+error);
				}else {
					String insertedkey = myCompany.getNewObjectCode();
					orders.getByKey(Integer.parseInt(insertedkey));
					oDocs.release();
					return convertSAPOrderToC2SOrder();
				}
			} catch (SBOCOMException e) {
				String error = myCompany.getLastErrorDescription();
				int errorcode = myCompany.getLastErrorCode();
				throw new NoSAPB1Exception("Could not create document... errorcode:"+errorcode+" description:"+error);
			}
			
		} else {//is update
			
			orders.getByKey(orderdockey);
			return convertSAPOrderToC2SOrder();
			
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
			case "amazon":
				sufix = "AZ";
				break;
			case "shopify":
				sufix = "SH";
				break;
			case "linio":
				sufix = "LI";
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
				ServiceLogger.error(new C2SRCServiceException("could not find and cast conversion. longValue is instance of "+longValue.getClass()));
			}
		}
		return valueTwo;
	}
	
	public static boolean isValidEmail(String emailStr) {
	        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX .matcher(emailStr);
	        return matcher.find();
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	private JSONArray convertSAPProductToC2SProducts() {
		
		IFields prodfields = productsrecordset.getFields();
		IItemWarehouseInfo whsinfo = products.getWhsInfo();
		IItems_Prices prodprices = products.getPriceList();
		IUoMPrices uomprices = prodprices.getUoMPrices();
		IItemUnitOfMeasurements units = products.getUnitOfMeasurements();
		IUnitOfMeasurementGroupParamsCollection unitslist = uomgrpsvc.getList();
		IItemBarCodes barcodes = products.getBarCodes();
		String thisbarcode = products.getBarCode();
		
		JSONObject product = new JSONObject();
		product.put("_id",""+products.getItemCode());
		product.put("sku",""+products.getItemCode());
		product.put("title",""+products.getItemName());
		product.put("url","");
		if(manufacturers.getByKey(products.getManufacturer())) {
			product.put("brand",""+manufacturers.getManufacturerName());
		} else {
			product.put("brand","");
		}
		product.put("mpn","");
		product.put("model","");
		product.put("description",""+products.getUser_Text());
		
		for(int i=0; i<prodfields.getCount(); i++) {
			IField item = prodfields.item(i);
			try {
				if(item.getName().equals("UpdateDate")) {
					Date updatedate = (Date) item.getValue();
					String updatedatewithpaging;
					updatedatewithpaging = updatedate.getTime()+" - "+offsetproductscurr;
					product.put("last_updated", updatedatewithpaging);
					ServiceLogger.log("product last_updated = "+product.get("last_updated"));
					offsetproductscurr=offsetproductscurr+1;
				}
				if(item.getName().equals("U_Modelo")) {
					product.put("brand",""+item.getValue());
				}
				if(item.getName().equals("U_Color")) {
					product.put("model",""+item.getValue());
				}
				if(item.getName().equals("U_Material")) {
					//map other fields
				}
				if(item.getName().equals("ItmsGrpCod")) {
					//map other fields
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
			if(delegate.config.getProperty("warehousestoaggregate").equals("all")) {
				
				for(int i=0; i<whsinfo.getCount(); i++) {
					whsinfo.setCurrentLine(i);
					JSONObject availability = new JSONObject();
					availabilities.add(availability);
					availability.put("tag",""+whsinfo.getWarehouseCode().trim());
					availability.put("quantity",(int)whsinfo.getInStock().doubleValue()+(int)whsinfo.getOrdered().doubleValue()-(int)whsinfo.getCommitted().doubleValue());
				}
			} else {
				String warehousestoaggregate = delegate.config.getProperty("warehousestoaggregate");
				String[] subsetwarehousecodes = warehousestoaggregate.split(",");
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
			}
		} else {
			JSONObject availability = new JSONObject();
			availabilities.add(availability);
			availability.put("tag","default");
			availability.put("quantity",products.getQuantityOnStock()+0);
		}

		JSONArray prices = new JSONArray();
		variation.put("prices",prices);
		JSONObject price = new JSONObject();
		prices.add(price);
		price.put("tag", "default");
		price.put("currency", delegate.config.getProperty("defaultcurrency"));
		String pricelistoverride = delegate.config.getProperty("pricelistoverride");
		double priceval = prodprices.getPrice();
		if(pricelistoverride != null && pricelistoverride.length() > 0) {
			for(int i=0; i<prodprices.getCount(); i+=1) {
				prodprices.setCurrentLine(i);
				if(prodprices.getPriceListName().equals(pricelistoverride)) {
					priceval = prodprices.getPrice();
				}
			}
		}
		price.put("number", priceval+0);
		
		String imgurl = "";
		String picurl = products.getPicture();
		JSONArray images = new JSONArray();
		variation.put("images",images);
		
		if(picurl.length()>0) {
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			String imgpath = delegate.config.getProperty("picturesfolder")+picurl;
			try {
				BufferedImage image = ImageIO.read(new File(imgpath));
			    ImageIO.write(image, "png", baos);
			} catch (IOException e) {
			    ServiceLogger.error(new C2SRCServiceException("Could not read supposed image from SAP ("+imgpath+"). "+e.getMessage()));
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
		
		variation.put("barcode", ""+products.getBarCode());
		variation.put("size", "");
		variation.put("color", "");

		JSONArray productsArray = new JSONArray();
		
		if(delegate.config.getProperty("explodeproductsbyuom").equals("true")) {
			
			String grouplistconfig = delegate.config.getProperty("explodeproductsbyuomgroups");
			String[] groupsconfigured = grouplistconfig.split(",");
			
			for(int i=0; i<unitslist.getCount(); i+=1) {
				
				IUnitOfMeasurementGroupParams groupparams = unitslist.item(i);
				IUnitOfMeasurementGroup group = uomgrpsvc.get(groupparams);
				IUoMGroupDefinitionCollection groupdef = group.getGroupDefinitions();
				
				boolean validgroup = false;
				for(int j=0; j<groupsconfigured.length; j+=1) {
					if(groupsconfigured[j].equals(groupparams.getCode())) {
						validgroup = true;
					}
				}
				
				if(validgroup) {
					
					for(int j=0; j<groupdef.getCount(); j+=1) {
						
						IUoMGroupDefinition itmdef = groupdef.item(j);
						JSONObject explodedprod;
						int packagesize = itmdef.getBaseQuantity().intValue();
						
						try {
							
							JSONParser parser = new JSONParser();
							explodedprod = (JSONObject) parser.parse(product.toJSONString());
							double thisprice = priceval;
							
							for(int m=0; m<uomprices.getCount(); m+=1) {
								uomprices.setCurrentLine(m);
								if(uomprices.getUoMEntry().equals(itmdef.getAlternateUoM())){
									double possibleuomprice = uomprices.getPrice().doubleValue();
									if(possibleuomprice > 0) {
										thisprice = possibleuomprice;
									}
								}
							}
							
							for(int m=0; m<barcodes.getCount(); m+=1) {
								barcodes.setCurrentLine(m);
								if(barcodes.getUoMEntry().equals(itmdef.getAlternateUoM())){
									String possibleuombarcode = barcodes.getBarCode();
									if(possibleuombarcode != null && possibleuombarcode.length() > 0) {
										thisbarcode = possibleuombarcode;
									}
								}
							}
							
							if(explodedprod != null) {
								if(explodedprod.containsKey("variations")) {
									JSONArray explodedvars = (JSONArray) explodedprod.get("variations");
									for(int k=0; k<explodedvars.size(); k+=1) {
										JSONObject explodedvar = (JSONObject) explodedvars.get(k);
										if(explodedvar.containsKey("availabilities")) {
											JSONArray explodedavs = (JSONArray) explodedvar.get("availabilities");
											for(int l=0; l<explodedavs.size(); l+=1) {
												JSONObject explodedav = (JSONObject) explodedavs.get(l);
												int currentstock = ((Long) explodedav.get("quantity")).intValue();
												if(packagesize != 0) {
													int therightstock = (int)(currentstock/packagesize);
													explodedav.put("quantity", therightstock);											
												}else {
													int therightstock = currentstock;
													explodedav.put("quantity", currentstock);
												}
											}
										}
										if(explodedvar.containsKey("prices")) {
											JSONArray explodedprcs = (JSONArray) explodedvar.get("prices");
											for(int l=0; l<explodedprcs.size(); l+=1) {
												JSONObject explodedprc = (JSONObject) explodedprcs.get(l);
												explodedprc.put("number", thisprice*packagesize);
											}
										}
										if(explodedvar.containsKey("barcode")) {
											String barcode = ""+explodedvar.get("barcode");
											if(thisbarcode != null && thisbarcode.length() > 0) {
												explodedvar.put("barcode", thisbarcode);
											}
										}
									}
								}
							}
							String currentsku = ""+explodedprod.get("sku");
							explodedprod.put("_id", currentsku+"-"+packagesize);
							explodedprod.put("sku", currentsku+"-"+packagesize);
							productsArray.add(explodedprod);
						}catch(Exception e) {
							ServiceLogger.error(new C2SRCServiceException("Could not explode product by clonation. "+e.getMessage()));
						}
						
					}
					
				}
				
			}
			
		} else {
			productsArray.add(product);
		}
		
		return productsArray;
		
	}
	
	@SuppressWarnings("unchecked")
	private JSONObject convertSAPOrderToC2SOrder() {
		
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
		
		IContactEmployees employees = customers.getContactEmployees();
		IDocument_Lines orderlines = orders.getLines();

		String querybyid = "SELECT * FROM dbo.OCRD WHERE [CardCode] = ordercardcode";
		String overridedquerybyid = delegate.config.getProperty("filterquerysqloverrideordersbyid");
		if(overridedquerybyid != null && overridedquerybyid.length() > 0) {
			querybyid = overridedquerybyid;
		}
		querybyid = querybyid.replace("ordercardcode", "'"+orders.getCardCode()+"'");
		
		customersrecordset.doQuery(querybyid);
		customers.getBrowser().setRecordset(customersrecordset);
		
		//customers.getByKey(orders.getCardCode());
		String buyer_email = ""+customers.getEmailAddress();
		String buyer_phone = ""+customers.getPhone1();
		if(buyer_phone.equals("")) {
			buyer_phone = ""+customers.getPhone2();
			if(buyer_phone.equals("")) {
				if(buyer_phone.equals("")) {
					buyer_phone = ""+employees.getPhone1();
					if(buyer_phone.equals("")) {
						buyer_phone = ""+employees.getPhone2();
						if(buyer_phone.equals("")) {
							buyer_phone = ""+employees.getMobilePhone();
						}
					}
				}
			}
		}
		String buyer_firstName = ""+employees.getFirstName();
		if(buyer_firstName.equals("")) {
			buyer_firstName = ""+employees.getName();
		}
		String buyer_lastName = ""+employees.getLastName();
		if(buyer_lastName.equals("")) {
			buyer_lastName = ""+customers.getContactPerson();
		}
		JSONObject buyer = new JSONObject();
		buyer.put("id", buyer_id);
		buyer.put("email", buyer_email);
		buyer.put("phone", buyer_phone);
		buyer.put("firstName", buyer_firstName);
		buyer.put("lastName", buyer_lastName);
		
		JSONArray orderItems = new JSONArray();
		for(int i=0; i<orderlines.getCount(); i++) {
			JSONObject orderItem = new JSONObject();
			orderlines.setCurrentLine(i);
			String itemcode = ""+orderlines.getItemCode();
			if(!delegate.config.getProperty("shippingitemcode").equals("") && itemcode.equals(delegate.config.getProperty("shippingitemcode"))) {
				orderItem.put("id", "shipping");
			}else if(!delegate.config.getProperty("surchargeitemcode").equals("") && itemcode.equals(delegate.config.getProperty("surchargeitemcode"))) {
				orderItem.put("id", "surcharge");
			}else {
				orderItem.put("id", itemcode);
			}
			orderItem.put("variation_id", "");
			orderItem.put("quantity", orderlines.getQuantity()+0);
			orderItem.put("unitPrice", orderlines.getUnitPrice()+0);
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
			IFields ordersfields = ordersrecordset.getFields();
			for(int i=0; i<ordersfields.getCount(); i++) {
				IField orderfield = ordersfields.item(i);
				try {
					if(orderfield.getName().equals("UpdateDate")) {
						Date updatedate = (Date) orderfield.getValue();
						String updatedatewithpaging;
						updatedatewithpaging = updatedate.getTime()+" - "+offsetorderscurr;
						order.put("last_updated", updatedatewithpaging);
						ServiceLogger.log("order last_updated = "+order.get("last_updated"));
						offsetorderscurr=offsetorderscurr+1;
						break;
					}
				}catch(Exception e) {}
			}
		}
		
		return order;
		
	}

	public void testbuildc2sproduct(String c2sprodid) throws NoSAPB1Exception {
		
		setProductsStreamCursor("0");
		products.getByKey(c2sprodid);
		JSONArray c2sprods = convertSAPProductToC2SProducts();
		System.out.println("c2sprods.toJSONString="+c2sprods.toJSONString());
		
	}
	
	public void testquotationorder() {
		
		Scanner sc = new Scanner(System.in);
		String raworder = "";
		while(sc.hasNextLine()) {
			String thisline = sc.nextLine(); 
			raworder += thisline+"\n";
		}
		sc.close();
		JSONParser parser = new JSONParser();
		try {
			JSONObject order = (JSONObject) parser.parse(raworder);
			try {
				JSONObject ordersap = this.storeOrder(order);
				System.out.println(ordersap.toJSONString());
			} catch (NoSAPB1Exception e) {
				e.printStackTrace();
			}
		} catch (org.json.simple.parser.ParseException e) {
			e.printStackTrace();
		}
		
	}

}