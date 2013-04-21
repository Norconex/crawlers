/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.filter;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.collector.http.HttpCollectorException;

/**
 * Convenience base class for implementing filters offering the include/exclude
 * "onmatch" option.  Default behavior on match is to include.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
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
