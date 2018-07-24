package com.click2sync.rc.srv.sapb1;

import java.util.Properties;
import org.json.simple.JSONObject;

import com.sap.smb.sbo.api.Company;
import com.sap.smb.sbo.api.IBusinessPartners;
import com.sap.smb.sbo.api.Recordset;
import com.sap.smb.sbo.api.SBOCOMConstants;
import com.sap.smb.sbo.api.SBOCOMException;
import com.sap.smb.sbo.api.SBOCOMUtil;

public class BusinessPartnerAutoGeneration {
	
	public String generateBusinessPartner(Company myCompany, Properties defaults, JSONObject input) throws SBOCOMException, NoSAPB1Exception {
		
		IBusinessPartners businessPartner = SBOCOMUtil.newBusinessPartners(myCompany);
		
		//buyer info
		String completename = (""+input.get("firstName")+" "+input.get("lastName")).trim();
		businessPartner.setCardName(completename);//FName + LName
		businessPartner.setAdditionalID(""+input.get("id"));//Reference ID
		businessPartner.setPhone1(""+input.get("phone"));//Phone1
		businessPartner.setEmailAddress(""+input.get("email"));//Email
		
		//default
		Recordset autoIdRecSet = (Recordset) SBOCOMUtil.newRecordset(myCompany);
		autoIdRecSet.doQuery("SELECT 'C' + REPLACE(STR(MAX(CAST(SUBSTRING(T1.CardCode, 2, LEN(T1.CardCode)) AS INTEGER)) + 1, 7), ' ', 0)  FROM OCRD T1 WHERE T1.CardType = 'C'");
		String newid = ""+autoIdRecSet.getFields().item(0).getValue();
		autoIdRecSet.release();
		businessPartner.setCardCode(newid);
		businessPartner.setCardType(SBOCOMConstants.BoCardTypes_cCustomer);	
		businessPartner.setGroupCode(0);//GroupCode
		businessPartner.setCurrency("");//$,MXN
		businessPartner.setFederalTaxID("");//RFC
		businessPartner.setShippingType(0);//ShippingTypeId
		businessPartner.setSalesPersonCode(0);//SalesEmployee
		businessPartner.setBusinessType("");//Business Partner Type Id
		businessPartner.setIndustry(0);//Industry ID
		businessPartner.getBPPaymentMethods().setPaymentMethodCode("");//Default Payment Method Code
		
		int docadded = businessPartner.add();
		if(docadded != 0) {
			String error = myCompany.getLastErrorDescription();
			int errorcode = myCompany.getLastErrorCode();
			throw new NoSAPB1Exception("Could not store business partner... errorcode:"+errorcode+" result:"+docadded+" description:"+error);
		}else {
			String insertedkey = myCompany.getNewObjectCode();
			return insertedkey;
		}
		
	}

}
