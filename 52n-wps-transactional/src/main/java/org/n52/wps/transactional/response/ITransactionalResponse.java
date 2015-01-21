package org.n52.wps.transactional.response;

import java.io.InputStream;

import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.response.IResponse;

/**
 * Interface for any transactional response Classes have to implement
 * getAsStream function to make sure to provide an inputStream for the
 * HttpServletResponse
 * 
 * @author woessner
 * 
 */

public interface ITransactionalResponse extends IResponse {

	public abstract InputStream getAsStream() throws ExceptionReport;

}
