package ca.sqlpower.sql;

import java.io.*;
import java.text.*;
import java.sql.SQLException;

/**
 * The base class for utilities that format a {@link WebResultSet}
 * into various human-readable formats.  Currently, HTML output is
 * available.  CSV is planned for the near future.
 *
 * @author Jonathan Fuerth
 * @version $Id$
 */
public abstract class WebResultFormatter {

    protected String rowidParameterName;
    protected String checkboxYesValue;
    protected NumberFormat numberFormatter;
    protected NumberFormat moneyFormatter;
    protected NumberFormat percentFormatter;
    protected DateFormat dateFormatter;

    public WebResultFormatter() {
	rowidParameterName="row_id";
	checkboxYesValue="YES";
	numberFormatter=new DecimalFormat("#,##0.#");
	moneyFormatter=new DecimalFormat("$#,##0.00");
	percentFormatter=new DecimalFormat("0%");
	dateFormatter=DateFormat.getDateInstance();
    }

    /**
     * Gets the desired parameter name for the row identifier column.
     *
     * @return The row identifier parameter name.
     */
    public String getRowidParameterName() {
	return rowidParameterName;
    }

    /**
     * Gets the desired parameter name for the row identifier column.
     *
     * @param newName The new row identifier parameter name.
     */
    public void setRowidParameterName(String newName) {
        rowidParameterName=newName;
    }

    /**
     * Gets the value that indicates a "checked" (on) indicator in the
     * database.
     *
     * @return the string representing "true"
     */
    public String getCheckboxYesValue() {
	return checkboxYesValue;
    }
    
    /**
     * Sets the value that indicates a "checked" (on) indicator in the
     * database. All other values are considered to mean "false."
     *
     * @param v The new value of the string representing "true."
     */
    public void setCheckboxYesValue(String  v) {
	this.checkboxYesValue = v;
    }    

    public void setNumberFormatter(NumberFormat v) {
	numberFormatter=v;
    }

    public void setMoneyFormatter(NumberFormat v) {
	moneyFormatter=v;
    }

    public void setPercentFormatter(NumberFormat v) {
	percentFormatter=v;
    }

    public void setDateFormatter(DateFormat v) {
	dateFormatter=v;
    }

    protected String beautifyHeading(String heading) {
	StringBuffer newHeading=new StringBuffer(heading);

	for(int i=0; i<newHeading.length(); i++) {
	    if(newHeading.charAt(i) == '_') {
		newHeading.setCharAt(i, ' ');
	    }
	}
	return newHeading.toString();
    }

    protected void getColumnFormatted(WebResultSet wrs,
				      int i,
				      StringBuffer contents,
				      StringBuffer align) 
	throws SQLException, NoRowidException, ColumnNotDisplayableException {
	int type=wrs.getColumnType(i);
	
	switch(type) {
	case FieldTypes.NUMBER:
	    align.append("right");
	    contents.append(numberFormatter.format(wrs.getFloat(i)));
	    break;

	default:
	case FieldTypes.NAME:
	    align.append("left");
	    contents.append(wrs.getString(i));
	    break;
	    
	case FieldTypes.MONEY:
	    align.append("right");
	    contents.append(moneyFormatter.format(wrs.getFloat(i)));
	    break;

	case FieldTypes.BOOLEAN:
	    align.append("center");
	    String tmp=wrs.getString(i);
	    if(tmp != null) {
		contents.append("True");
	    } else {
		contents.append("False");
	    }
	    break;
	    
	case FieldTypes.RADIO:
	    align.append("center");
	    contents.append("<input type=\"radio\" name=\"")
		.append(wrs.getColumnLabel(i))
		.append("\" value=\"")
		.append(wrs.getRowid())
		.append("\" onClick=\"this.form.submit()\" />");
	    break;

	case FieldTypes.CHECKBOX:
	    align.append("center");
	    contents.append("<input type=\"checkbox\" name=\"")
		.append(wrs.getColumnLabel(i))
		.append("\" value=\"")
		.append(wrs.getRowid())
		.append("\"");
	    if(wrs.getString(i).equals(checkboxYesValue)) {
		contents.append(" checked");
	    }
	    contents.append(" />");
	    break;

	case FieldTypes.PERCENT:
	    align.append("right");
	    try {
		contents.append(percentFormatter.format(wrs.getFloat(i)/100));
	    } catch(SQLException e) {
		// Non-numeric values cause a number-conversion problem
		contents.append(wrs.getString(i));
	    }
	    break;
	    
	case FieldTypes.DATE:
	    align.append("center");
	    java.sql.Date date=wrs.getDate(i);
	    if(date==null) {
		contents.append("(null)");
	    } else {
		contents.append(
		    dateFormatter.format(new java.util.Date(date.getTime()))
		    );
	    }
	    break;

	case FieldTypes.ALPHANUM_CODE:
	    align.append("center");
	    contents.append(wrs.getString(i));
	    break;

	case FieldTypes.ROWID:
	case FieldTypes.DUMMY:
	    throw new ColumnNotDisplayableException();
	}
    }

    public abstract void formatToStream(WebResultSet wrs, PrintWriter out) 
	throws SQLException, NoRowidException;
}
