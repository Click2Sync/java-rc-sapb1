package com.click2sync.rc.srv.sapb1;

import org.boris.winrun4j.*;

public class ServiceTest extends AbstractService {

	@Override
	public int serviceMain(String[] args) throws ServiceException {
		
		while(!shutdown) {
			Main.abstractLoop();
		}
		return 0;
	}

}