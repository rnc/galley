/**
 * Copyright (C) 2012 Red Hat, Inc. (jdcasey@commonjava.org)
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
package org.commonjava.maven.galley.spi.cache;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Transfer;

public interface CacheProvider
{
    String SUFFIX_TO_DELETE = ".to-delete";

    String SUFFIX_TO_DOWNLOAD = ".to-download";

    Set<String> HIDDEN_SUFFIXES = Collections.unmodifiableSet( new HashSet<String>()
    {
        {
            add( SUFFIX_TO_DELETE );
            add( SUFFIX_TO_DOWNLOAD );
        }

        private static final long serialVersionUID = 1L;

    } );

    boolean isDirectory( ConcreteResource resource );

    boolean isFile( ConcreteResource resource );

    InputStream openInputStream( ConcreteResource resource )
        throws IOException;

    OutputStream openOutputStream( ConcreteResource resource )
        throws IOException;

    boolean exists( ConcreteResource resource );

    void copy( ConcreteResource from, ConcreteResource to )
        throws IOException;

    String getFilePath( ConcreteResource resource );

    boolean delete( ConcreteResource resource )
        throws IOException;

    String[] list( ConcreteResource resource );

    File getDetachedFile( ConcreteResource resource );

    void mkdirs( ConcreteResource resource )
        throws IOException;

    void createFile( ConcreteResource resource )
        throws IOException;

    void createAlias( ConcreteResource from, ConcreteResource to )
        throws IOException;

    Transfer getTransfer( ConcreteResource resource );

    void clearTransferCache();

    long length( ConcreteResource resource );

    long lastModified( ConcreteResource resource );

    boolean isReadLocked( ConcreteResource resource );

    boolean isWriteLocked( ConcreteResource resource );

    void unlockRead( ConcreteResource resource );

    void unlockWrite( ConcreteResource resource );

    void lockRead( ConcreteResource resource );

    void lockWrite( ConcreteResource resource );

    void waitForWriteUnlock( ConcreteResource resource );

    void waitForReadUnlock( ConcreteResource resource );

}
