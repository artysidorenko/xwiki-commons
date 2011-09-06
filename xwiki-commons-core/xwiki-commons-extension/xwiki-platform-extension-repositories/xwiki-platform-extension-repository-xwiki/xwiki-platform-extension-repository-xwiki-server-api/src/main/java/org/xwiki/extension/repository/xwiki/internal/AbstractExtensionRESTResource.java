/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xwiki.extension.repository.xwiki.internal;

import java.util.List;
import java.util.Vector;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.context.Execution;
import org.xwiki.extension.repository.xwiki.model.jaxb.Extension;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionDependency;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionSummary;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionVersion;
import org.xwiki.extension.repository.xwiki.model.jaxb.ExtensionVersionSummary;
import org.xwiki.extension.repository.xwiki.model.jaxb.Extensions;
import org.xwiki.extension.repository.xwiki.model.jaxb.ObjectFactory;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.rest.Utils;
import org.xwiki.rest.XWikiResource;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.api.Property;

/**
 * Base class for the annotation REST services, to implement common functionality to all annotation REST services.
 * 
 * @version $Id$
 * @since 3.2M3
 */
public abstract class AbstractExtensionRESTResource extends XWikiResource implements Initializable
{
    protected final static String EXTENSION_CLASSNAME = "XWiki.ExtensionClass";

    protected final static String EXTENSIONVERSION_CLASSNAME = "XWiki.ExtensionVersionClass";

    protected final static String EXTENSIONDEPENDENCY_CLASSNAME = "XWiki.ExtensionVersionClass";

    /**
     * The execution needed to get the annotation author from the context user.
     */
    @Inject
    protected Execution execution;

    /**
     * The object factory for model objects to be used when creating representations.
     */
    protected ObjectFactory objectFactory;

    @Override
    public void initialize() throws InitializationException
    {
        super.initialize();

        this.objectFactory = new ObjectFactory();
    }

    protected Query createExtensionsQuery(String from, String where, int offset, int number, boolean versions)
        throws QueryException
    {
        // select

        String select =
            "extension.id, extension.type, extension.name, extension.lastVersion"
                + ", extension.description, extension.website, extension.authors, extension.features";

        // TODO: add support for lists: GROUP_CONCAT seems to exist only in MySQL and can't find any HQL or JPQL
        // solution yet
        // * authors
        // * features
        // * dependencies

        return createExtensionsQuery(select, from, where, offset, number, versions);
    }

    protected Query createExtensionsSummariesQuery(String from, String where, int offset, int number, boolean versions)
        throws QueryException
    {
        String select = "extension.id, extension.type, extension.name";

        return createExtensionsQuery(select, from, where, offset, number, versions);
    }

    private Query createExtensionsQuery(String select, String from, String where, int offset, int number,
        boolean versions) throws QueryException
    {
        // select

        StringBuilder queryStr = new StringBuilder("select ");
        queryStr.append(select);

        if (versions) {
            queryStr.append(", extensionVersion.version");
        }

        // from

        queryStr.append(" from Document doc, doc.object(" + EXTENSION_CLASSNAME + ") as extension");

        if (versions) {
            queryStr.append(", doc.object(" + EXTENSIONVERSION_CLASSNAME + ") as extensionVersion");
        }

        // where

        if (from != null) {
            queryStr.append(',');
            queryStr.append(from);
        }

        if (where != null) {
            queryStr.append(" where ");
            queryStr.append(where);
        }

        Query query = this.queryManager.createQuery(queryStr.toString(), Query.XWQL);

        if (offset > 0) {
            query.setOffset(offset);
        }
        if (number > 0) {
            query.setLimit(number);
        }

        return query;
    }

    protected Document getExtensionDocument(String extensionId) throws XWikiException, QueryException
    {
        Query query =
            this.queryManager.createQuery("from doc.object(" + EXTENSION_CLASSNAME
                + ") where extension.id = :extensionId", Query.XWQL);

        query.bindValue("extensionId", extensionId);

        List<String> documentNames = query.execute();

        if (documentNames.isEmpty()) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        return Utils.getXWikiApi(this.componentManager).getDocument(documentNames.get(0));
    }

    protected com.xpn.xwiki.api.Object getExtensionObject(Document extensionDocument)
    {
        return extensionDocument.getObject(EXTENSION_CLASSNAME);
    }

    protected com.xpn.xwiki.api.Object getExtensionObject(String extensionId, String extensionVersion)
        throws XWikiException, QueryException
    {
        return getExtensionObject(getExtensionDocument(extensionId));
    }

    protected com.xpn.xwiki.api.Object getExtensionVersionObject(Document extensionDocument, String version)
    {
        if (version == null) {
            Vector<com.xpn.xwiki.api.Object> objects = extensionDocument.getObjects(EXTENSIONVERSION_CLASSNAME);

            if (objects.isEmpty()) {
                return null;
            } else {
                return objects.lastElement();
            }
        }

        return extensionDocument.getObject(EXTENSIONVERSION_CLASSNAME, "version", version, false);
    }

    protected com.xpn.xwiki.api.Object getExtensionVersionObject(String extensionId, String version)
        throws XWikiException, QueryException
    {
        return getExtensionVersionObject(getExtensionDocument(extensionId), version);
    }

    protected Extension createExtension(Document extensionDocument, String version)
    {
        com.xpn.xwiki.api.Object extensionObject = getExtensionObject(extensionDocument);

        if (extensionObject == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        com.xpn.xwiki.api.Object extensionVersionObject = getExtensionVersionObject(extensionDocument, version);

        if (extensionVersionObject == null) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }

        Extension extension;
        ExtensionVersion extensionVersion;
        if (version == null) {
            extension = this.objectFactory.createExtension();
            extensionVersion = null;
        } else {
            extension = extensionVersion = this.objectFactory.createExtensionVersion();
            extensionVersion.setVersion((String) getValue(extensionVersionObject, "version"));
        }

        extension.setId((String) getValue(extensionObject, "id"));
        extension.setType((String) getValue(extensionObject, "type"));

        extension.getAuthors().addAll((List<String>) getValue(extensionObject, "authors"));
        extension.setDescription((String) getValue(extensionObject, "description"));
        extension.setName((String) getValue(extensionObject, "name"));
        extension.setWebsite((String) getValue(extensionObject, "website"));
        extension.getFeatures().addAll((List<String>) getValue(extensionObject, "features"));

        if (extensionVersion != null) {
            for (com.xpn.xwiki.api.Object dependencyObject : extensionDocument.getObjects(
                EXTENSIONDEPENDENCY_CLASSNAME, "extensionversion", version)) {
                ExtensionDependency dependency = new ExtensionDependency();
                dependency.setId((String) getValue(dependencyObject, "id"));
                dependency.setVersion((String) getValue(dependencyObject, "version"));

                extensionVersion.getDependencies().add(dependency);
            }
        }

        return extension;
    }

    protected Extensions getExtensionSummaries(Query query) throws QueryException
    {
        Extensions extensions = this.objectFactory.createExtensions();

        getExtensionSummaries(extensions.getExtensionSummaries(), query);

        return extensions;
    }

    protected void getExtensions(List<ExtensionVersion> extensions, Query query) throws QueryException
    {
        List<Object[]> entries = query.execute();

        for (Object[] entry : entries) {
            extensions.add(createExtensionVersionFromQueryResult(entry));
        }
    }

    private ExtensionVersion createExtensionVersionFromQueryResult(Object[] entry)
    {
        ExtensionVersion extension = this.objectFactory.createExtensionVersion();

        extension.setId((String) entry[0]);
        extension.setVersion((String) entry[1]);
        extension.setType((String) entry[2]);
        extension.setName((String) entry[3]);
        extension.setDescription((String) entry[4]);
        extension.setWebsite((String) entry[5]);

        // TODO: add support for
        // * authors
        // * features
        // * dependencies

        return extension;
    }

    protected <E extends ExtensionSummary> void getExtensionSummaries(List<E> extensions, Query query)
        throws QueryException
    {
        List<Object[]> entries = query.execute();

        for (Object[] entry : entries) {
            extensions.add((E) createExtensionSummaryFromQueryResult(entry));
        }
    }

    private ExtensionSummary createExtensionSummaryFromQueryResult(Object[] entry)
    {
        ExtensionSummary extension;
        ExtensionVersionSummary extensionVersion;
        if (entry.length == 3) {
            extension = this.objectFactory.createExtensionSummary();
            extensionVersion = null;
        } else {
            extension = extensionVersion = this.objectFactory.createExtensionVersionSummary();
            extensionVersion.setVersion((String) entry[3]);
        }

        extension.setId((String) entry[0]);
        extension.setType((String) entry[1]);
        extension.setName((String) entry[2]);

        return extension;
    }

    protected Object getValue(com.xpn.xwiki.api.Object object, String field)
    {
        return getValue(object, field, null);
    }

    protected <T> T getValue(com.xpn.xwiki.api.Object object, String field, T def)
    {
        Property property = object.getProperty(field);

        return property != null ? (T) property.getValue() : def;
    }

    protected XWikiContext getXWikiContext()
    {
        return (XWikiContext) execution.getContext().getProperty("xwikicontext");
    }
}
