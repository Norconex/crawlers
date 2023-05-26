package com.norconex.committer.idol;

import java.io.Writer;
import java.net.URL;
import java.util.List;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.commons.lang.url.HttpURL;


/* Delete/add params are set by IDOL Client prior to calling
 * "prepare".
 */
interface IIdolIndexAction {

    URL url(List<CommitterRequest> batch, HttpURL startUrl)
            throws CommitterException;

    void writeTo(List<CommitterRequest> batch, Writer writer)
            throws CommitterException;



//    IHttpBodyWriter prepare(List<ICommitterRequest> batch, HttpURL startUrl)
//            throws CommitterException;
//
//    @FunctionalInterface
//    interface IHttpBodyWriter {
//        void writeTo(Writer writer) throws CommitterException;
//    }
}
