package org.ohmage.request.easypost;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ohmage.request.Request;

public class EasyPostRequest extends Request {

	public EasyPostRequest(HttpServletRequest httpRequest) {
		super(httpRequest);
	}

	@Override
	public void service() {
		
	}

	@Override
	public Map<String, String[]> getAuditInformation() {
		return null;
	}

	@Override
	public void respond(HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		
		
	}
	

}
