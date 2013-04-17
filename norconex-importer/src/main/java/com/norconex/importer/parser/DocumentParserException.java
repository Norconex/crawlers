/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.parser;

/**
 * Exception thrown upon encountering a non-recoverable issue parsing a
 * document.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class DocumentParserException extends Exception {

    private static final long serialVersionUID = -8668185121797858885L;

    public DocumentParserException() {
    }

    public DocumentParserException(String message) {
        super(message);
    }

    public DocumentParserException(Throwable cause) {
        super(cause);
    }

    public DocumentParserException(String message, Throwable cause) {
        super(message, cause);
    }

}
