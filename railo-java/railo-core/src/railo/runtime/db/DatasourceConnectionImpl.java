package railo.runtime.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import railo.commons.digest.MD5;
import railo.runtime.op.Caster;

/**
 * wrap for datasorce and connection from it
 */
public final class DatasourceConnectionImpl implements DatasourceConnectionPro {
    
    private static final int MAX_PS = 100;
	private Connection connection;
    private DataSource datasource;
    private long time;
	private String username;
	private String password;
	private int transactionIsolationLevel=-1;
	private int requestId=-1;
	private Boolean supportsGetGeneratedKeys;

    /**
     * @param connection
     * @param datasource
     * @param pass  
     * @param user 
     */
    public DatasourceConnectionImpl(Connection connection, DataSource datasource, String username, String password) {
        this.connection = connection;
        this.datasource = datasource;
        this.time=System.currentTimeMillis();
        this.username = username;
        this.password = password;
        
        if(username==null) {
        	this.username=datasource.getUsername();
        	this.password=datasource.getPassword();
        }
        if(this.password==null)this.password="";
		
    }
    
    /**
     * @see railo.runtime.db.DatasourceConnection#getConnection()
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * @see railo.runtime.db.DatasourceConnection#getDatasource()
     */
    public DataSource getDatasource() {
        return datasource;
    }

    /**
     * @see railo.runtime.db.DatasourceConnection#isTimeout()
     */
    public boolean isTimeout() {
        int timeout=datasource.getConnectionTimeout();
        if(timeout<1)timeout=1;
        timeout*=60000;      
        return (time+timeout)<System.currentTimeMillis();
    }

	public DatasourceConnection using() {
		time=System.currentTimeMillis();
		return this;
	}

	/**
	 * @return the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @return the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 *
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if(this==obj) return true;
		
		if(!(obj instanceof DatasourceConnectionImpl)) return false;
		return equals(this, (DatasourceConnection) obj);
		
		
		/*if(!(obj instanceof DatasourceConnectionImpl)) return false;
		DatasourceConnectionImpl other=(DatasourceConnectionImpl) obj;
		
		if(!datasource.equals(other.datasource)) return false;
		//print.out(username+".equals("+other.username+") && "+password+".equals("+other.password+")");
		return username.equals(other.username) && password.equals(other.password);*/
	}
	
	public static boolean equals(DatasourceConnection left,DatasourceConnection right) {
		
		if(!left.getDatasource().equals(right.getDatasource())) return false;
		return left.getUsername().equals(right.getUsername()) && left.getPassword().equals(right.getPassword());
		
	}
	
	

	/**
	 * @return the transactionIsolationLevel
	 */
	public int getTransactionIsolationLevel() {
		return transactionIsolationLevel;
	}

	
	public int getRequestId() {
		return requestId;
	}
	public void setRequestId(int requestId) {
		this.requestId=requestId;
	}

	/**
	 * @see railo.runtime.db.DatasourceConnectionPro#supportsGetGeneratedKeys()
	 */
	public boolean supportsGetGeneratedKeys() {
		if(supportsGetGeneratedKeys==null){
			try {
				supportsGetGeneratedKeys=Caster.toBoolean(getConnection().getMetaData().supportsGetGeneratedKeys());
			} catch (Throwable t) {
				return false;
			}
		}
		return supportsGetGeneratedKeys.booleanValue();
	}
	
	private Map<String,PreparedStatement> preparedStatements=new HashMap<String, PreparedStatement>();
	
	/**
	 * @see railo.runtime.db.DatasourceConnectionPro#getPreparedStatement(railo.runtime.db.SQL, boolean)
	 */
	public PreparedStatement getPreparedStatement(SQL sql, boolean createGeneratedKeys) throws SQLException {
		// create key
		String strSQL=sql.getSQLString();
		String key=strSQL.trim()+":"+createGeneratedKeys;
		try {
			key = MD5.getDigestAsString(key);
		} catch (IOException e) {}
		PreparedStatement ps = preparedStatements.get(key);
		if(ps!=null) return ps;
		
		if(createGeneratedKeys)	ps= getConnection().prepareStatement(strSQL,Statement.RETURN_GENERATED_KEYS);
		else ps=getConnection().prepareStatement(strSQL);
		if(preparedStatements.size()>MAX_PS)
			closePreparedStatements((preparedStatements.size()-MAX_PS)+1);
		preparedStatements.put(key,ps);
		return ps;
	}
	

	/**
	 * @see railo.runtime.db.DatasourceConnectionPro#getPreparedStatement(railo.runtime.db.SQL, boolean)
	 */
	public PreparedStatement getPreparedStatement(SQL sql, int resultSetType,int resultSetConcurrency) throws SQLException {
		// create key
		String strSQL=sql.getSQLString();
		String key=strSQL.trim()+":"+resultSetType+":"+resultSetConcurrency;
		try {
			key = MD5.getDigestAsString(key);
		} catch (IOException e) {}
		PreparedStatement ps = preparedStatements.get(key);
		if(ps!=null) return ps;
		
		ps=getConnection().prepareStatement(strSQL,resultSetType,resultSetConcurrency);
		if(preparedStatements.size()>MAX_PS)
			closePreparedStatements((preparedStatements.size()-MAX_PS)+1);
		preparedStatements.put(key,ps);
		return ps;
	}
	

	/**
	 * @see railo.runtime.db.DatasourceConnectionPro#close()
	 */
	public void close() throws SQLException {
		closePreparedStatements(-1);
		getConnection().close();
	}
	

	public void closePreparedStatements(int maxDelete) throws SQLException {
		Iterator<Entry<String, PreparedStatement>> it = preparedStatements.entrySet().iterator();
		int count=0;
		while(it.hasNext()){
			try {
				Entry<String, PreparedStatement> entry = it.next();
				entry.getValue().close();
				it.remove();
				if(maxDelete!=0 && ++count>=maxDelete) break;
			} 
			catch (SQLException e) {}
		}
		
	}
	
	
	/*protected void finalize() throws Throwable {
	    try {
	        close();        // close open files
	    } finally {
	        super.finalize();
	    }
	}*/
}