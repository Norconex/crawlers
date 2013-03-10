package com.norconex.collector.http.filter;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.collector.http.HttpCollectorException;

/**
 * Convenience base class for implementing filters offering the include/exclude
 * "onmatch" option.  Default behavior on match is to include.
 * @author Pascal Essiembre
 */
public abstract class AbstractOnMatchFilter implements IXMLConfigurable {
	private static final long serialVersionUID = 40888302227887743L;
	
	private OnMatch onMatch = OnMatch.INCLUDE;

	public OnMatch getOnMatch() {
		return onMatch;
	}

	public void setOnMatch(OnMatch onMatch) {
		if (onMatch == null) {
			throw new IllegalArgumentException(
					"OnMatch argument cannot be null.");
		}
		this.onMatch = onMatch;
	}
	
	/**
	 * Convinience method for loading the "onMatch" attribute from an XML
	 * file when {@link XMLConfiguration} is used.
	 * @param xml the XML to load the "onMatch" attribute.
	 * @return the OnMatch matching the attribute value, or INCLUDE if it cannot
	 * be determined.
	 */
	protected OnMatch getOnMatch(XMLConfiguration xml) {
        OnMatch onMatch = OnMatch.INCLUDE;
        String onMatchStr = xml.getString(
        		"[@onMatch]", OnMatch.INCLUDE.toString()).toUpperCase();
        try {
        	onMatch = OnMatch.valueOf(onMatchStr);
        } catch (IllegalArgumentException e)  {
        	throw new HttpCollectorException("Configuration error: "
                    + "Invalid \"onMatch\" attribute value: \"" + onMatchStr
        			+ "\".  Must be one of \"include\" or \"exclude\".");
        }
        return onMatch;
	}

    @Override
    public String toString() {
        return "AbstractOnMatchFilter [onMatch=" + onMatch + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((onMatch == null) ? 0 : onMatch.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AbstractOnMatchFilter other = (AbstractOnMatchFilter) obj;
        if (onMatch != other.onMatch)
            return false;
        return true;
    }
}
