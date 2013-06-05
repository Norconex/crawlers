package com.norconex.importer.filter;

import java.io.Serializable;

/**
 * Tells the collector that a filter is of "OnMatch" type.  This means,
 * if one or more filters of type "include" exists in a set of filters, 
 * at least one of them must be matched for a document (or other object)
 * to be "included".  Only one filter of type "exclude" needs to be 
 * matched or the document (or other object) to be excluded.
 * Filters of type "exclude" have precedence over includes.
 * @author Pascal Essiembre
 */
public interface IOnMatchFilter extends Serializable {

    /**
     * Gets the the on match action (exclude or include).
     * @return on match (exclude or include)
     */
    OnMatch getOnMatch();
}
