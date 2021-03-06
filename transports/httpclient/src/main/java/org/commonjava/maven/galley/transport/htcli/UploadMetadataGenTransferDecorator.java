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
package org.commonjava.maven.galley.transport.htcli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.io.AbstractTransferDecorator;
import org.commonjava.maven.galley.model.SpecialPathInfo;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.io.SpecialPathManager;
import org.commonjava.maven.galley.transport.htcli.model.HttpExchangeMetadata;
import org.commonjava.maven.galley.transport.htcli.model.HttpExchangeMetadataFromRequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.Alternative;
import javax.inject.Named;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import static org.commonjava.maven.galley.spi.cache.CacheProvider.STORE_HTTP_HEADERS;

/**
 * This TransferDecorator is used to generate http-metadata.json when uploading artifacts. It will use {@link EventMetadata}
 * as intermediate to get the http request headers from client, and use these headers as the content for the json file. Only
 * artifacts but not metadata files will have the accompanied http-metadata file generation.
 */
@Named
@Alternative
public class UploadMetadataGenTransferDecorator
        extends AbstractTransferDecorator
{
    private static final Logger logger = LoggerFactory.getLogger( UploadMetadataGenTransferDecorator.class );

    private SpecialPathManager specialPathManager;

    public UploadMetadataGenTransferDecorator( SpecialPathManager specialPathManager )
    {
        this.specialPathManager = specialPathManager;
    }

    @Override
    public OutputStream decorateWrite( OutputStream stream, Transfer transfer, TransferOperation op,
                                       EventMetadata metadata )
            throws IOException
    {
        final SpecialPathInfo specialPathInfo = specialPathManager.getSpecialPathInfo( transfer, metadata.getPackageType() );

        final Boolean isMetadata = specialPathInfo != null && specialPathInfo.isMetadata();
        final Boolean isUpload = op == TransferOperation.UPLOAD;
        final Boolean hasRequestHeaders =
                metadata.get( STORE_HTTP_HEADERS ) != null && metadata.get( STORE_HTTP_HEADERS ) instanceof Map;

        // We only care about non-metadata artifact uploading for http-metadata generation
        if ( isUpload && !isMetadata && hasRequestHeaders )
        {
            @SuppressWarnings( "unchecked" ) Map<String, List<String>> storeHttpHeaders =
                    (Map<String, List<String>>) metadata.get( STORE_HTTP_HEADERS );
            writeMetadata( transfer, new ObjectMapper(), storeHttpHeaders );
        }

        return super.decorateWrite( stream, transfer, op, metadata );
    }

    private void writeMetadata( final Transfer target, final ObjectMapper mapper, final Map<String, List<String>> requestHeaders )
    {
        Transfer metaTxfr = target.getSiblingMeta( HttpExchangeMetadata.FILE_EXTENSION );
        if ( metaTxfr == null )
        {
            if ( target.isDirectory() )
            {
                logger.trace( "DIRECTORY. Using HTTP exchange metadata file INSIDE directory called: {}", HttpExchangeMetadata.FILE_EXTENSION );
                metaTxfr = target.getChild( HttpExchangeMetadata.FILE_EXTENSION );
            }
            else
            {
                logger.trace( "SKIP: Cannot retrieve HTTP exchange metadata Transfer instance for: {}", target );
                return;
            }
        }

        final HttpExchangeMetadata metadata = new HttpExchangeMetadataFromRequestHeader( requestHeaders );
        OutputStream out = null;
        try
        {
            final Transfer finalMeta = metaTxfr;
            out = metaTxfr.openOutputStream( TransferOperation.GENERATE, false );
            logger.trace( "Writing HTTP exchange metadata:\n\n{}\n\n", new Object()
            {
                @Override
                public String toString()
                {
                    try
                    {
                        return mapper.writeValueAsString( metadata );
                    }
                    catch ( final JsonProcessingException e )
                    {
                        logger.warn( String.format("Failed to write HTTP exchange metadata: %s. Reason: %s", finalMeta, e.getMessage()), e );
                    }

                    return "ERROR RENDERING METADATA";
                }
            } );

            out.write( mapper.writeValueAsBytes( metadata ) );
        }
        catch ( final IOException e )
        {
            if ( logger.isTraceEnabled() )
            {
                logger.trace( String.format( "Failed to write metadata for HTTP exchange to: %s. Reason: %s", metaTxfr,
                                             e.getMessage() ), e );
            }
            else
            {
                logger.warn( "Failed to write metadata for HTTP exchange to: {}. Reason: {}", metaTxfr, e.getMessage() );
            }
        }
        finally
        {
            IOUtils.closeQuietly( out );
        }
    }
}
