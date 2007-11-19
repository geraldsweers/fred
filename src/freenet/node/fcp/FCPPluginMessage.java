/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.fcp;

import freenet.node.Node;
import freenet.pluginmanager.FCPPluginOutputWrapper;
import freenet.pluginmanager.FredPluginFCP;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

/**
 * @author saces
 * 
 * FCPPluginMessage
 * Identifer=me
 * PluginName=plugins.HelloFCP (or plugins.HelloFCP.HelloFCP?)
 * Param.Itemname1=value1
 * Param.Itemname2=value2
 * ...
 * 
 * EndMessage
 *    or
 * DataLength=datasize
 * Data
 * <datasize> bytes of data
 * 
 */
public class FCPPluginMessage extends DataCarryingMessage {
	
	public static final String NAME = "FCPPluginMessage";
	
	public static final String PARAM_PREFIX = "Param";
	
	private final String identifier;
	private final String pluginname;
	
	private final long dataLength;
	
	private final SimpleFieldSet plugparams;
	
	FCPPluginMessage(SimpleFieldSet fs) throws MessageInvalidException {
		identifier = fs.get("Identifier");
		if(identifier == null)
			throw new MessageInvalidException(ProtocolErrorMessage.MISSING_FIELD, "FCPPluginMessage must contain a Identifier field", null, false);
		pluginname = fs.get("PluginName");
		if(pluginname == null)
			throw new MessageInvalidException(ProtocolErrorMessage.MISSING_FIELD, "FCPPluginMessage must contain a PluginName field", null, false);
		
		boolean havedata = "Data".equals(fs.getEndMarker());
		
		String dataLengthString = fs.get("DataLength");
		
		if(!havedata && (dataLengthString != null))
			throw new MessageInvalidException(ProtocolErrorMessage.INVALID_FIELD, "A nondata message can't have a DataLength field", identifier, false);

		if(havedata) {
			if (dataLengthString == null)
				throw new MessageInvalidException(ProtocolErrorMessage.MISSING_FIELD, "Need DataLength on a Datamessage", identifier, false);
		
			try {
				dataLength = Long.parseLong(dataLengthString, 10);
			} catch (NumberFormatException e) {
				throw new MessageInvalidException(ProtocolErrorMessage.ERROR_PARSING_NUMBER, "Error parsing DataLength field: "+e.getMessage(), identifier, false);
			}
		} else {
			dataLength = -1;
		}
		
		plugparams = fs.subset(PARAM_PREFIX);
	}

	String getIdentifier() {
		return identifier;
	}

	boolean isGlobal() {
		return false;
	}

	long dataLength() {
		return dataLength;
	}

	public SimpleFieldSet getFieldSet() {
		return null;
	}

	public String getName() {
		return NAME;
	}

	public void run(final FCPConnectionHandler handler, final Node node) throws MessageInvalidException {

		final Bucket data2 = this.bucket;
		final FCPPluginOutputWrapper replysender = new FCPPluginOutputWrapper(handler, pluginname, identifier);
		node.executor.execute(new Runnable() {

			public void run() {
				Logger.normal(this, "Searching fcp plugin: " + pluginname);
				FredPluginFCP plug = node.pluginManager.getFCPPlugin(pluginname);
				if (plug == null) {
					Logger.error(this, "Could not find fcp plugin: " + pluginname);
					return;
				}
				Logger.normal(this, "Found fcp plugin: " + pluginname);
				
				try {
					plug.handle(replysender, plugparams, data2, handler.hasFullAccess());
				} catch (Throwable t) {
					Logger.error(this, "Cought error while execute fcp plugin handler" + t.getMessage(), t);
				}
			
			}
		}, "FCPPlugin runner for " + pluginname);

	}

}
