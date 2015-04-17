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
package org.commonjava.maven.galley.io.checksum;

import java.io.IOException;

import org.commonjava.maven.galley.io.checksum.Sha512GeneratorFactory.Sha512Generator;
import org.commonjava.maven.galley.model.Transfer;

public final class Sha512GeneratorFactory
    extends AbstractChecksumGeneratorFactory<Sha512Generator>
{

    public Sha512GeneratorFactory()
    {
    }

    @Override
    protected Sha512Generator newGenerator( final Transfer transfer )
        throws IOException
    {
        return new Sha512Generator( transfer );
    }

    public static final class Sha512Generator
        extends AbstractChecksumGenerator
    {

        protected Sha512Generator( final Transfer transfer )
            throws IOException
        {
            super( transfer, ".sha512", "SHA-512" );
        }

    }

}
