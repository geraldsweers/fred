package freenet.node.fcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.MalformedURLException;

import freenet.client.async.ClientRequester;
import freenet.keys.FreenetURI;
import freenet.support.Fields;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;

/**
 * A request process carried out by the node for an FCP client.
 * Examples: ClientGet, ClientPut, MultiGet.
 */
public abstract class ClientRequest {

	/** URI to fetch, or target URI to insert to */
	protected final FreenetURI uri;
	/** Unique request identifier */
	protected final String identifier;
	/** Verbosity level. Relevant to all ClientRequests, although they interpret it
	 * differently. */
	protected final int verbosity;
	/** Original FCPConnectionHandler. Null if persistence != connection */
	protected final FCPConnectionHandler origHandler;
	/** Client */
	protected final FCPClient client;
	/** Priority class */
	protected short priorityClass;
	/** Persistence type */
	protected final short persistenceType;
	/** Has the request finished? */
	protected boolean finished;
	/** Client token (string to feed back to the client on a Persistent* when he does a
	 * ListPersistentRequests). */
	protected String clientToken;
	/** Is the request on the global queue? */
	protected final boolean global;

	public ClientRequest(FreenetURI uri2, String identifier2, int verbosity2, FCPConnectionHandler handler, 
			FCPClient client, short priorityClass2, short persistenceType2, String clientToken2, boolean global) {
		this.uri = uri2;
		this.identifier = identifier2;
		this.verbosity = verbosity2;
		this.finished = false;
		this.priorityClass = priorityClass2;
		this.persistenceType = persistenceType2;
		this.clientToken = clientToken2;
		this.global = global;
		if(persistenceType == PERSIST_CONNECTION)
			this.origHandler = handler;
		else
			origHandler = null;
		this.client = client;
	}
	
	public ClientRequest(FreenetURI uri2, String identifier2, int verbosity2, FCPConnectionHandler handler, 
			short priorityClass2, short persistenceType2, String clientToken2, boolean global) {
		this.uri = uri2;
		this.identifier = identifier2;
		this.verbosity = verbosity2;
		this.finished = false;
		this.priorityClass = priorityClass2;
		this.persistenceType = persistenceType2;
		this.clientToken = clientToken2;
		this.global = global;
		if(persistenceType == PERSIST_CONNECTION)
			this.origHandler = handler;
		else
			origHandler = null;
		if(global) {
			client = handler.server.globalClient;
		} else {
			client = handler.getClient();
		}
	}

	public ClientRequest(SimpleFieldSet fs, FCPClient client2) throws MalformedURLException {
		uri = new FreenetURI(fs.get("URI"));
		identifier = fs.get("Identifier");
		verbosity = Integer.parseInt(fs.get("Verbosity"));
		persistenceType = ClientRequest.parsePersistence(fs.get("Persistence"));
		if(persistenceType == ClientRequest.PERSIST_CONNECTION)
			throw new IllegalArgumentException("Reading persistent get with type CONNECTION !!");
		if(!(persistenceType == ClientRequest.PERSIST_FOREVER || persistenceType == ClientRequest.PERSIST_REBOOT))
			throw new IllegalArgumentException("Unknown persistence type "+ClientRequest.persistenceTypeString(persistenceType));
		this.client = client2;
		this.origHandler = null;
		clientToken = fs.get("ClientToken");
		finished = Fields.stringToBool(fs.get("Finished"), false);
		global = Fields.stringToBool(fs.get("Global"), false);
	}

	/** Lost connection */
	public abstract void onLostConnection();
	
	/** Send any pending messages for a persistent request e.g. after reconnecting */
	public abstract void sendPendingMessages(FCPConnectionOutputHandler handler, boolean includePersistentRequest, boolean includeData);

	// Persistence
	
	static final short PERSIST_CONNECTION = 0;
	static final short PERSIST_REBOOT = 1;
	static final short PERSIST_FOREVER = 2;
	
	public static String persistenceTypeString(short type) {
		switch(type) {
		case PERSIST_CONNECTION:
			return "connection";
		case PERSIST_REBOOT:
			return "reboot";
		case PERSIST_FOREVER:
			return "forever";
		default:
			return Short.toString(type);
		}
	}

	public static short parsePersistence(String string) {
		if(string == null || string.equalsIgnoreCase("connection"))
			return PERSIST_CONNECTION;
		if(string.equalsIgnoreCase("reboot"))
			return PERSIST_REBOOT;
		if(string.equalsIgnoreCase("forever"))
			return PERSIST_FOREVER;
		return Short.parseShort(string);
	}

	public static ClientRequest readAndRegister(BufferedReader br, FCPServer server) throws IOException {
		SimpleFieldSet fs = new SimpleFieldSet(br, true);
		String clientName = fs.get("ClientName");
		boolean isGlobal = Fields.stringToBool(fs.get("Global"), false);
		FCPClient client;
		if(!isGlobal)
			client = server.registerClient(clientName, server.node, null);
		else
			client = server.globalClient;
		try {
			String type = fs.get("Type");
			if(type.equals("GET")) {
				ClientGet cg = new ClientGet(fs, client);
				client.register(cg);
				return cg;
			} else if(type.equals("PUT")) {
				ClientPut cp = new ClientPut(fs, client);
				client.register(cp);
				return cp;
			} else if(type.equals("PUTDIR")) {
				ClientPutDir cp = new ClientPutDir(fs, client);
				client.register(cp);
				return cp;
			} else {
				Logger.error(ClientRequest.class, "Unrecognized type: "+type);
				return null;
			}
		} catch (Throwable t) {
			Logger.error(ClientRequest.class, "Failed to parse: "+t, t);
			return null;
		}
	}

	public void cancel() {
		ClientRequester cr = getClientRequest();
		// It might have been finished on startup.
		if(cr != null) cr.cancel();
	}

	public boolean isPersistentForever() {
		return persistenceType == ClientRequest.PERSIST_FOREVER;
	}

	/** Is the request persistent? False = we can drop the request if we lose the connection */
	public boolean isPersistent() {
		return persistenceType != ClientRequest.PERSIST_CONNECTION;
	}

	public boolean hasFinished() {
		return finished;
	}

	/** Get identifier string for request */
	public String getIdentifier() {
		return identifier;
	}

	public void setPriorityClass(short priorityClass) {
		this.priorityClass = priorityClass;
		getClientRequest().setPriorityClass(priorityClass);
	}

	public void setClientToken(String clientToken) {
		this.clientToken = clientToken;
	}

	protected abstract ClientRequester getClientRequest();
	
	/** Completed request dropped off the end without being acknowledged */
	public void dropped() {
		cancel();
		freeData();
	}

	/** Free cached data bucket(s) */
	protected abstract void freeData(); 

	/** Request completed. But we may have to stick around until we are acked. */
	protected void finish() {
		if(persistenceType == ClientRequest.PERSIST_CONNECTION)
			origHandler.finishedClientRequest(this);
		client.finishedClientRequest(this);
	}
	
	/**
	 * Write a persistent request to disk.
	 * @throws IOException 
	 */
	public void write(BufferedWriter w) throws IOException {
		if(persistenceType == ClientRequest.PERSIST_CONNECTION) {
			Logger.error(this, "Not persisting as persistenceType="+persistenceType);
			return;
		}
		// Persist the request to disk
		SimpleFieldSet fs = getFieldSet();
		fs.writeTo(w);
	}
	
	/**
	 * Get a SimpleFieldSet representing this request.
	 */
	public abstract SimpleFieldSet getFieldSet() throws IOException;

	public abstract double getSuccessFraction();

	public abstract String getFailureReason();
}
