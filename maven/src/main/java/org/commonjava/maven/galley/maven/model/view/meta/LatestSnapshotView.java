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
package org.commonjava.maven.galley.maven.model.view.meta;

import java.text.ParseException;
import java.util.Date;

import org.commonjava.maven.atlas.ident.util.SnapshotUtils;
import org.w3c.dom.Element;

public class LatestSnapshotView
    extends MavenMetadataElementView
{

    public LatestSnapshotView( final MavenMetadataView xmlView, final Element element )
    {
        super( xmlView, element );
    }

    public boolean isLocalCopy()
    {
        final String val = getValue( "localCopy" );
        return val == null ? false : Boolean.parseBoolean( val );
    }

    public Date getTimestamp()
        throws ParseException
    {
        final String val = getValue( "timestamp" );
        return val == null ? null : SnapshotUtils.parseSnapshotTimestamp( val );
    }

    public Integer getBuildNumber()
    {
        final String val = getValue( "buildNumber" );
        return val == null ? null : Integer.parseInt( val );
    }

}
