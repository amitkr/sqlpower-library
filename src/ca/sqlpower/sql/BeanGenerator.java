import java.sql.*;
import java.io.*;
import java.util.*;
import ca.sqlpower.sql.*;

public class BeanGenerator {
	public static final String VERSION_STRING="SQLPower BeanGenerator v1.0";
	PrintWriter out;
	ResultSetMetaData md;
	String tableName;
	String className;
	String packageName;
	String uniqueIdColName;

	public BeanGenerator(ResultSet rs,
						 String tableName,
						 String packageName,
						 String destDir)
		throws SQLException, IOException {
		md=rs.getMetaData();
		this.tableName=tableName;
		className=convertToClassName(tableName);
		List priKey=SQL.findPrimaryKey(rs.getStatement().getConnection(),
									   tableName);
		if(priKey.size() != 1) {
			throw new SQLException("The table "+tableName
								   +" does not have a single-column primary "
								   +"key (it has a "+priKey.size()
								   +"-column key).");
		}
		uniqueIdColName=(String)priKey.get(0);

		try {
			out=new PrintWriter(new FileOutputStream(destDir+className+".java"));
			outputPackageStatement();
			outputImports();
			outputDeclaration();
			outputConstructor();
			outputStoreMethod();
			outputFindMethod();
			outputSetGetMethods();
			outputEnding();
		} finally {
			out.close();
		}
	}

	void outputPackageStatement() {
		if(packageName != null && packageName.length() > 0) {
			out.print("package ");
			out.println(packageName);
			out.println();
		}
	}

	void outputImports() {
		out.println("import java.sql.*;");
		out.println("import ca.sqlpower.sql.SQL;");
		out.println();
	}
	
	void outputDeclaration() throws SQLException {
		out.println("/**");
		out.println(" * The "+className+" class is a database persistence ");
		out.println(" * object which is tightly coupled with the underlying ");
		out.println(" * "+tableName+" database table.");
		out.println(" * ");
		out.println(" * @author Autogenerated by "+VERSION_STRING);
		out.println(" * @version Autogenerated on "+new java.util.Date());
		out.println(" */");		
		out.println("public class "+className+" {");
		out.print("    protected static final String UNIQUE_ID_COL_NAME=\"");
		out.print(uniqueIdColName);
		out.println("\";");
		out.println("    protected boolean _alreadyInDatabase;");
		for(int col=1; col<=md.getColumnCount(); col++) {
			out.print("    protected String ");
			out.print(convertToMemberName(md.getColumnName(col)));
			out.println(";");
		}
		out.println();
	}

	void outputConstructor() throws SQLException {
		out.println("    public "+className+"() {");
		out.println("        _alreadyInDatabase=false;");
		for(int col=1; col<=md.getColumnCount(); col++) {
			out.print("        ");
			out.print(convertToMemberName(md.getColumnName(col)));
			out.println("=null;");
		}
		out.println("    }");
		out.println();
	}

	void outputStoreMethod() throws SQLException {
		// Create the UPDATE statement
		StringBuffer upd=new StringBuffer(1000);
		upd.append("UPDATE ");
		upd.append(tableName);
		upd.append(" SET (");
		for(int col=1; col<=md.getColumnCount(); col++) {
			if(col>1) upd.append(", ");
			upd.append(md.getColumnName(col));
			upd.append("=\"+SQL.quote(");
			upd.append(convertToMemberName(md.getColumnName(col)));
			upd.append(")+\"");
		}
		upd.append(") WHERE \"+UNIQUE_ID_COL_NAME+\" = \"+");
		upd.append("SQL.quote(getUniqueId())");

		// Create the INSERT statement
		StringBuffer ins=new StringBuffer(1000);
		ins.append("INSERT INTO ");
		ins.append(tableName);
		ins.append("(");
		for(int col=1; col<=md.getColumnCount(); col++) {
			if(col>1) ins.append(", ");
			ins.append(md.getColumnName(col));
		}
		ins.append(") VALUES (");
		for(int col=1; col<=md.getColumnCount(); col++) {
			if(col>1) ins.append(", ");
			ins.append("\"+SQL.quote(");
			ins.append(convertToMemberName(md.getColumnName(col)));
			ins.append(")+\"");
		}
		ins.append(")\"");

		// Output the method that uses it
		out.println("    public void store(Connection con) throws SQLException {");
		out.println("        Statement stmt=null;");
		out.println("        try {");
		out.println("            stmt=con.createStatement();");
		out.println("            if(_alreadyInDatabase) {");
		out.println("                stmt.executeUpdate(\""+upd.toString()+");");
		out.println("            } else {");
		out.println("                stmt.executeUpdate(\""+ins.toString()+");");
		out.println("                _alreadyInDatabase=true;");
		out.println("            }");
		out.println("        } finally {");
		out.println("            if(stmt != null) {");
		out.println("                stmt.close();");
		out.println("            }");
		out.println("        }");
		out.println("    }");
	}
	
	void outputFindMethod() throws SQLException {
		// Create the SELECT statement
		StringBuffer sql=new StringBuffer(1000);
		sql.append("SELECT ");
		for(int col=1; col<=md.getColumnCount(); col++) {
			if(col>1) sql.append(", ");
			sql.append(md.getColumnName(col));
		}
		sql.append(" FROM ");
		sql.append(tableName);
		sql.append(" WHERE ");
		sql.append(uniqueIdColName);
		sql.append(" = \"+");
		sql.append("SQL.quote(");
		sql.append(convertToMemberName(uniqueIdColName));
		sql.append(")+\""); // needs to end "inside" the string
		

		out.print("    public static ");
		out.print(className);
		out.print(" findByPrimaryKey(Connection con, String ");
		out.print(convertToMemberName(uniqueIdColName));
		out.println(") throws SQLException {");
		out.println("        Statement stmt=null;");
		out.println("        ResultSet rs=null;");
		out.println("        try {");
		out.println("            stmt=con.createStatement();");
		out.println("            rs=stmt.executeQuery(\""+sql.toString()+"\");");
		out.println("            if(!rs.next()) { return null; }");
		out.println("            "+className+" newBean=new "+className+"();");
		out.println("            newBean._alreadyInDatabase=true;");
		for(int col=1; col<=md.getColumnCount(); col++) {
			out.print("            newBean.set");
			out.print(convertToClassName(md.getColumnName(col)));
			out.print("(rs.getString(");
			out.print(col);
			out.println("));");
		}
		out.println("            return newBean;");
		out.println("        } finally {");
		out.println("            if(stmt != null) {");
		out.println("                stmt.close();");
		out.println("            }");
		out.println("        }");
		out.println("    }");
	}

	void outputSetGetMethods() throws SQLException {
		out.println("    public String getUniqueId() {");
		out.print("        return get");
		out.print(convertToClassName(uniqueIdColName));
		out.println("();");
		out.println("    }");
		out.println();

		for(int col=1; col<=md.getColumnCount(); col++) {
			String Member=convertToClassName(md.getColumnName(col));
			String member=convertToMemberName(md.getColumnName(col));
			out.println("    public String get"+Member+"() {");
			out.print("        return ");
			out.print(member);
			out.println(";");
			out.println("    }");
			out.println();
			out.println("    public void set"+Member+"(String v) {");
			out.print("        ");
			out.print(member);
			out.println("=v;");
			out.println("    }");
			out.println();
		}
	}

	void outputEnding() throws SQLException {
		out.println("}");
	}

	static String convertToClassName(String underscores) {
		return convertToCamelCaps(underscores, true);
	}

	static String convertToMemberName(String underscores) {
		return convertToCamelCaps(underscores, false);
	}
	
	static String convertToCamelCaps(String underscores, boolean initialCap) {
		boolean nextCharIsUpper=initialCap;
		StringBuffer camelCaps=new StringBuffer(underscores.length());
		for(int srcIndex=0; srcIndex<underscores.length(); srcIndex++) {
			char c=underscores.charAt(srcIndex);
			if(c=='_') {
				nextCharIsUpper=true;
				continue;
			}
			if(nextCharIsUpper) {
				c=Character.toUpperCase(c);
				nextCharIsUpper=false;
			} else {
				c=Character.toLowerCase(c);
			}
			camelCaps.append(c);
		}
		return camelCaps.toString();
	}

	public static void main(String args[]) throws Exception {

		// Lookup the database named in args[0] in databases.xml
		String dbxml="databases.xml";
		String name=args[0];
		InputStream in=new FileInputStream(dbxml);
		DBConnectionSpec dbcs=DBConnectionSpec.getDBSpecFromInputStream(in, name);
		if(dbcs==null) {
			System.err.println("No database definition '"+name+"' in "+dbxml+".");
			return;
		}
		String dbclass=dbcs.getDriverClass();
		String dburl=dbcs.getUrl();
		String dbuser=dbcs.getUser();
		String dbpass=dbcs.getPass();

		String tableName=args[1];
		Connection con;
		Statement stmt;
		ResultSet rs;
		ResultSetMetaData rsmd;

		Class.forName(dbclass).newInstance();

		con=DriverManager.getConnection(dburl, dbuser, dbpass);
		stmt=con.createStatement();
		rs=stmt.executeQuery("SELECT * FROM "+tableName);

		new BeanGenerator(rs, tableName, null, "");

		con.close();
	}
}
