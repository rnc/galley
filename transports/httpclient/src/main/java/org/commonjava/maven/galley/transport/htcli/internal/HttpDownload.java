/**
 * Copyright (C) 2013 Red Hat, Inc. (jdcasey@commonjava.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.commonjava.maven.galley.transport.htcli.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.commonjava.maven.galley.TransferContentException;
import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.transport.DownloadJob;
import org.commonjava.maven.galley.transport.htcli.Http;
import org.commonjava.maven.galley.transport.htcli.model.HttpLocation;
import org.commonjava.maven.galley.transport.htcli.util.HttpUtil;
import org.commonjava.maven.galley.util.ResourceUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.apache.commons.io.IOUtils.copy;
import static org.commonjava.maven.galley.spi.cache.CacheProvider.STORAGE_PATH;

public final class HttpDownload
    extends AbstractHttpJob
    implements DownloadJob
{

    private final Transfer target;

    private Map<Transfer, Long> transferSizes;

    private final EventMetadata eventMetadata;

    private final ObjectMapper mapper;

    private boolean deleteFilesOnPath;

    public HttpDownload( final String url, final HttpLocation location, final Transfer target,
                         final Map<Transfer, Long> transferSizes, final EventMetadata eventMetadata, final Http http,
                         final ObjectMapper mapper )
    {
        this( url, location, target, transferSizes, eventMetadata, http, mapper, true );
    }

    public HttpDownload( final String url, final HttpLocation location, final Transfer target,
                         final Map<Transfer, Long> transferSizes, final EventMetadata eventMetadata, final Http http,
                         final ObjectMapper mapper, final boolean deleteFilesOnPath )
    {
        super( url, location, http );
        this.target = target;
        this.transferSizes = transferSizes;
        this.eventMetadata = eventMetadata;
        this.mapper = mapper;
        this.deleteFilesOnPath = deleteFilesOnPath;
    }

    @Override
    public DownloadJob call()
    {
        request = new HttpGet( url );
        String oldName = Thread.currentThread().getName();
        try
        {
            String newName = oldName + ": GET " + url;
            Thread.currentThread().setName( newName );
            if ( executeHttp() )
            {
                transferSizes.put( target, HttpUtil.getContentLength( response ) );
                writeTarget();
            }
        }
        catch ( final TransferException e )
        {
            this.error = e;
        }
        finally
        {
            cleanup();
            if ( oldName != null )
            {
                Thread.currentThread().setName( oldName );
            }
        }

        logger.info( "Download attempt done: {} Result:\n  target: {}\n  error: {}", url, target, error );
        return this;
    }

    @Override
    protected ObjectMapper getMetadataObjectMapper()
    {
        return mapper;
    }

    @Override
    public long getTransferSize()
    {
        return response == null ? -1 : HttpUtil.getContentLength( response );
    }

    @Override
    public TransferException getError()
    {
        return error;
    }

    @Override
    public Transfer getTransfer()
    {
        if ( eventMetadata.get( STORAGE_PATH ) != null )
        {
            target.setResource( ResourceUtils.storageResource( target.getResource(), eventMetadata ) );
        }
        return target;
    }

    private void writeTarget()
        throws TransferException
    {
        OutputStream out = null;
        if ( response != null )
        {

            InputStream in = null;
            try
            {
                final HttpEntity entity = response.getEntity();

                in = entity.getContent();
                out = target.openOutputStream( TransferOperation.DOWNLOAD, true, eventMetadata, deleteFilesOnPath );
                doCopy( in, out );
                logger.info( "Ensuring all HTTP data is consumed..." );
            }
            catch ( final IOException eOrig )
            {
                closeAllQuietly( in, out );

                ConcreteResource resource = target.getResource();
                try
                {
                    logger.debug( "Failed to write to local proxy store:{}. Deleting partial target file:{}", eOrig,
                                  target.getPath() );
                    target.delete();
                }
                catch ( IOException eDel )
                {
                    logger.error( String.format( "Failed to delete target file: %s\nOriginal URL: %s. Reason: %s",
                                  target, url, eDel.getMessage() ), eDel );
                }

                logger.error( String.format( "Failed to write to local proxy store: %s\nOriginal URL: %s. Reason: %s", target, url,
                              eOrig.getMessage() ), eOrig );

                throw new TransferContentException( resource,
                                                    "Failed to write to local proxy store: %s\nOriginal URL: %s. Reason: %s",
                                                    eOrig, target, url, eOrig.getMessage() );
            }
            finally
            {
                closeAllQuietly( in, out );
            }
        }
    }

    /**
     * Break out {@link org.apache.commons.io.IOUtils#copy(InputStream, OutputStream)} so we can decorate it with Byteman
     * rules to test network errors.
     * @param in
     * @param out
     */
    private void doCopy( final InputStream in, final OutputStream out )
            throws IOException
    {
        copy( in, out );
    }

    private void closeAllQuietly( final InputStream in, final OutputStream out )
    {
        try
        {
            EntityUtils.consume( response.getEntity() );
            logger.info( "All HTTP data was consumed." );
        }
        catch ( IOException e )
        {
            logger.error( "Failed to consume remainder HTTP response entity data", e );
        }
        finally
        {
            closeQuietly( in );

            logger.info( "Closing output stream: {}", out );
            closeQuietly( out );
        }
    }

}
