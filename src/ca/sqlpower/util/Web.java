package ca.sqlpower.util;

import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;

/**
 * Some static methods that help with generating web applications.
 *
 * @author Jonathan Fuerth
 * @version $CVS$
 */
public class Web {
    /**
     * generates an html table of the paramater names and values of an
     * HttpServletRequest object.  This method is not expected to be
     * used in production; it is simply a debugging tool.
     *
     * @param req the request whose fields should be tabulated
     * @return an html <code>TABLE</code> element describing the request
     * @see #formatSessionAsTable(HttpSession)
     */
    public static String formatRequestAsTable(HttpServletRequest req) {
        StringBuffer sb = new StringBuffer(200);
        sb.append("<table border=\"1\">");
        for (Enumeration e = req.getParameterNames();e.hasMoreElements() ;) {
            String thisElement = (String)e.nextElement();
            sb.append("<tr><td>")
                .append(thisElement)
                .append("</td><td>[");
            String[] values=req.getParameterValues(thisElement);
            for(int i=0; i<values.length; i++) {
                sb.append(values[i]);
                if(i != values.length-1) {
                    sb.append(", ");
                }
            }
            sb.append("]</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    /**
     * generates an html table of the attribute names and classes of
     * an HttpSeession object.  This method is not expected to be used
     * in production; it is simply a debugging tool.
     *
     * @param s the session whose fields should be tabulated
     * @return an html <code>TABLE</code> element describing the session
     * @see #formatRequestAsTable(HttpServletRequest)
     */
    public static String formatSessionAsTable(HttpSession s) {
	Enumeration enum = s.getAttributeNames();
	StringBuffer sb = new StringBuffer(200);
	sb.append("<table border=\"1\">");
        while(enum.hasMoreElements()) {
            String thisElement = (String)enum.nextElement();
	    if(thisElement == null) {
		sb.append("<tr><td>NULL ELEMENT!</td></tr>");
	 continue;
	    }
            sb.append("<tr><td>")
                .append(thisElement)
                .append("</td><td>");
	    if(s.getAttribute(thisElement) == null) {
		sb.append("NULL!");
	    } else {
		sb.append(s.getAttribute(thisElement).getClass().getName());
	    }
            sb.append("</td></tr>");
	}
        sb.append("</table>");
        return sb.toString();
    }

    /**
     * @deprecated use the version that splits up the argument
     * hasAnyAll into hasAny and hasAll
     */
    public static String makeSelectionList(String name,
					   List options,
					   String defaultSelection,
					   boolean hasAnyAll) {
	return makeSelectionList(name, options, defaultSelection,
				 hasAnyAll, hasAnyAll);
    }

    public static String makeSelectionList(String name,
					   List options,
					   String defaultSelection,
					   boolean hasAny,
					   boolean hasAll) {
	StringBuffer out=new StringBuffer();
	String thisOption;
	if(defaultSelection == null) {
	    defaultSelection="!@#$%^&*() NO DEFAULT ()*&^%$#@!";
	}
	out.append("<select size=\"1\" name=\"");
	out.append(name);
	out.append("\">");
	
	if(hasAny) {
	    appendOption(out, "---Total---", defaultSelection.equals("---Total---"));
	}
	if(hasAll) {
	    appendOption(out, "---All---", defaultSelection.equals("---All---"));
	}

	ListIterator i=options.listIterator();
	while(i.hasNext()) {
	    thisOption=(String)i.next();

	    appendOption(out, thisOption, thisOption.equals(defaultSelection));
	}
	out.append("</select>");
        return out.toString();
    }

    private static void appendOption(StringBuffer sb, String optionName, boolean selected) {
	sb.append(" <option");
	if(selected) {
	    sb.append(" selected");
	}
	sb.append(">");
	sb.append(optionName);
	sb.append("</option>");
    }

	/** Check if the given string contains any special characters (<, >, &) */
	public static boolean containsHTMLMarkup(String testMe){
		for(int i=0; i<testMe.length(); i++){
			if(testMe.charAt(i)=='<' ||
			   testMe.charAt(i)=='>' ||
			   testMe.charAt(i)=='&'){

				return true;
			}
		} // end for (loop through testMe)
		return false;
	}
} // end class
 
