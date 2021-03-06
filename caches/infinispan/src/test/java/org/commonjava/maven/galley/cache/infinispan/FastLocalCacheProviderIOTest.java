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
package org.commonjava.maven.galley.cache.infinispan;

import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.SimpleLocation;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMScript;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith( org.jboss.byteman.contrib.bmunit.BMUnitRunner.class )
@BMUnitConfig( loadDirectory = "target/test-classes/bmunit/common", debug = true )
public class FastLocalCacheProviderIOTest
        extends AbstractFastLocalCacheBMUnitTest
{
    @Test
    @BMScript( "WriteReadLockVerify.btm" )
    public void testLockThenWaitForUnLock()
            throws Exception
    {
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String path = "my/path.txt";
        final ConcreteResource res = new ConcreteResource( loc, path );

        CountDownLatch latch = new CountDownLatch( 2 );

        start( new ReadLockThread( res, latch ) );
        start( new WriteLockThread( res, latch ) );
        latchWait( latch );

        assertThat( provider.isWriteLocked( res ), equalTo( false ) );
        assertThat( provider.isReadLocked( res ), equalTo( false ) );
    }

    @Test
    @BMScript( "SimultaneousWritesResourceExistence.btm" )
    public void testSimultaneousWritesResourceExistence()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String dir = "/path/to/my/";
        final String fname1 = dir + "file1.txt";
        final String fname2 = dir + "file2.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        start( new WriteFileThread( content, loc, fname1, latch ) );
        start( new WriteFileThread( content, loc, fname2, latch ) );
        latchWait( latch );

        assertThat( provider.exists( new ConcreteResource( loc, fname1 ) ), equalTo( true ) );
        assertThat( provider.exists( new ConcreteResource( loc, fname2 ) ), equalTo( true ) );
        assertThat( provider.isDirectory( new ConcreteResource( loc, dir ) ), equalTo( true ) );

        final Set<String> listing =
                new HashSet<String>( Arrays.asList( provider.list( new ConcreteResource( loc, dir ) ) ) );
        assertThat( listing.size() > 1, equalTo( true ) );
        assertThat( listing.contains( "file1.txt" ), equalTo( true ) );
        assertThat( listing.contains( "file2.txt" ), equalTo( true ) );
    }

    @Test
    @BMScript( "WriteDeleteAndVerifyNonExistence.btm" )
    public void testWriteDeleteAndVerifyNonExistence()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        start( new WriteFileThread( content, loc, fname, latch ) );
        start( new DeleteFileThread( loc, fname, latch ) );
        latchWait( latch );

        assertThat( provider.exists( new ConcreteResource( loc, fname ) ), equalTo( false ) );
    }

    @Test
    @BMScript( "ConcurrentWriteAndReadFile.btm" )
    public void ConcurrentWriteAndReadFile()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";

        CountDownLatch latch = new CountDownLatch( 2 );

        start( new ReadFileThread( loc, fname, latch ) );
        start( new WriteFileThread( content, loc, fname, latch ) );
        latchWait( latch );

        assertThat( result, equalTo( content ) );
    }

    /**
     * Two threads included in this test, CopyFileThread will copy the content generated by WriteFileThread and then read from the copied one.
     * The matcher result is expected be equal to the original content written from WriteFileThread.
     * @throws Exception
     */
    @Test
    @BMScript( "WriteCopyAndReadNewFile.btm" )
    public void writeCopyAndReadNewFile()
            throws Exception
    {
        final String content = "This is a test";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname = "/path/to/my/file.txt";
        final Location loc2 = new SimpleLocation( "http://bar.com" );

        CountDownLatch latch = new CountDownLatch( 2 );

        start( new CopyFileThread( loc, loc2, fname, latch ) );
        start( new WriteFileThread( content, loc, fname, latch ) );
        latchWait( latch );

        assertThat( result, equalTo( content ) );
    }

    @BMRule( name = "longTimeWaitBeforeCloseTest", targetClass = "FastLocalCacheProvider",
             targetMethod = "openOutputStream", targetLocation = "EXIT", action = "java.lang.Thread.sleep(60*1000)" )
    @Test
    public void testLongTimeWaitBeforeClose()
            throws IOException
    {
        final Location loc = new SimpleLocation( "http://foo.com" );
        ConcreteResource resource =
                new ConcreteResource( loc, String.format( "/path/to/my/%s", "file-write-close.text" ) );
        provider.openOutputStream( resource );
        provider.cleanupCurrentThread();
    }

    /**
     * File IO sequence as below: (file1 and file2 in same folder)
     *
     *   file1 open-> file1 close-> file2 open-> file2 close
     *
     */
    @Test
    public void testTwoFilesOpenInSameFolderAndSingleThreadInSequence()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path/to/my/file1.txt";
        final String fname2 = "/path/to/my/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        writeWithClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        writeWithClose( resource2, content2 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }

    /**
     * File IO sequence as below: (file1 and file2 NOT in same folder)
     *
     *   file1 open-> file1 close-> file2 open-> file2 close
     *
     */
    @Test
    public void testTwoFilesOpenInDiffFolderAndSingleThreadInSequence()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path1/to1/my1/file1.txt";
        final String fname2 = "/path2/to2/my2/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        writeWithClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        writeWithClose( resource2, content2 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }

    /**
     * File IO sequence as below: (file1 and file2 in same folder)
     *
     *   file1 open-> file2 open-> file2 close -> file1 close
     *
     */
    @Test
    public void testTwoFilesOpenInSameFolderAndSingleThreadDeSequenceCase1()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path/to/my/file1.txt";
        final String fname2 = "/path/to/my/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        OutputStream out1 = writeWithoutClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        OutputStream out2 = writeWithoutClose( resource2, content2 );

        out2.close();
        provider.unlockWrite( resource2 );

        out1.close();
        provider.unlockWrite( resource1 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }

    /**
     * File IO sequence as below: (file1 and file2 in same folder)
     *
     *   file1 open-> file2 open-> file1 close -> file2 close
     *
     */
    @Test
    public void testTwoFilesOpenInSameFolderAndSingleThreadDeSequenceCase2()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path/to/my/file1.txt";
        final String fname2 = "/path/to/my/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        OutputStream out1 = writeWithoutClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        OutputStream out2 = writeWithoutClose( resource2, content2 );

        out1.close();
        provider.unlockWrite( resource1 );

        out2.close();
        provider.unlockWrite( resource2 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }

    /**
     * File IO sequence as below: (file1 and file2 NOT in same folder)
     *
     *   file1 open-> file2 open-> file2 close -> file1 close
     *
     */
    @Test
    public void testTwoFilesOpenInDiffFolderAndSingleThreadDeSequenceCase1()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path1/to1/my1/file1.txt";
        final String fname2 = "/path2/to2/my2/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        OutputStream out1 = writeWithoutClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        OutputStream out2 = writeWithoutClose( resource2, content2 );

        out2.close();
        provider.unlockWrite( resource2 );

        out1.close();
        provider.unlockWrite( resource1 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }

    /**
     * File IO sequence as below: (file1 and file2 NOT in same folder)
     *
     *   file1 open-> file2 open-> file1 close -> file2 close
     *
     */
    @Test
    public void testTwoFilesOpenInDiffFolderAndSingleThreadDeSequenceCase2()
            throws IOException
    {
        final String content1 = "This is a test for f1";
        final String content2 = "This is a test for f2";
        final Location loc = new SimpleLocation( "http://foo.com" );
        final String fname1 = "/path1/to1/my1/file1.txt";
        final String fname2 = "/path2/to2/my2/file2.txt";

        ConcreteResource resource1 = new ConcreteResource( loc, fname1 );
        OutputStream out1 = writeWithoutClose( resource1, content1 );

        ConcreteResource resource2 = new ConcreteResource( loc, fname2 );
        OutputStream out2 = writeWithoutClose( resource2, content2 );

        out1.close();
        provider.unlockWrite( resource1 );

        out2.close();
        provider.unlockWrite( resource2 );

        String result1 = readWithClose( resource1 );

        assertThat( result1, equalTo( content1 ) );

        String result2 = readWithClose( resource2 );

        assertThat( result2, equalTo( content2 ) );
    }
}
