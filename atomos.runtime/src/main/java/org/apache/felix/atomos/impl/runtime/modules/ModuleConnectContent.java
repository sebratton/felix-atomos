/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.atomos.impl.runtime.modules;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.connect.ConnectContent;

public class ModuleConnectContent implements ConnectContent
{
    final Module module;
    final ModuleReference reference;
    final AtomosRuntimeModules atomosRuntime;
    final AtomicReference<Optional<Map<String, String>>> headers = new AtomicReference<>();
    volatile ModuleReader reader = null;

    public ModuleConnectContent(Module module, ModuleReference reference, AtomosRuntimeModules atomosRuntime)
    {
        this.module = module;
        this.reference = reference;
        this.atomosRuntime = atomosRuntime;
    }

    @Override
    public void open() throws IOException
    {
        reader = reference.open();
    }

    @Override
    public void close() throws IOException
    {
        ModuleReader current = reader;
        if (current != null)
        {
            reader = null;
            current.close();
        }
    }

    @Override
    public Optional<ClassLoader> getClassLoader()
    {
        return Optional.ofNullable(module.getClassLoader());
    }

    @Override
    public Iterable<String> getEntries() throws IOException
    {
        return () -> {
            try
            {
                return currentReader().list().iterator();
            }
            catch (IOException e)
            {
                throw new UncheckedIOException(e);
            }
        };

    }

    private ModuleReader currentReader() throws IOException
    {
        ModuleReader current = reader;
        if (current == null)
        {
            throw new IOException("Reader is not open.");
        }
        return current;
    }

    @Override
    public Optional<ConnectEntry> getEntry(String name)
    {
        try
        {
            return currentReader().find(name).map((u) -> new ModuleConnectEntry(name, u));
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }

    }

    @Override
    public Optional<Map<String, String>> getHeaders()
    {
        return headers.updateAndGet((h) -> {
            if (h == null)
            {
                h = atomosRuntime.createManifest(this, module);
            }
            return h;
        });
    }

    class ModuleConnectEntry implements ConnectEntry
    {
        final String name;
        final URI uri;

        public ModuleConnectEntry(String name, URI uri)
        {
            this.name = name;
            this.uri = uri;
        }

        @Override
        public long getContentLength()
        {
            try
            {
                return uri.toURL().openConnection().getContentLengthLong();
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return currentReader().open(name).get();
        }

        @Override
        public long getLastModified()
        {
            try
            {
                return uri.toURL().openConnection().getDate();
            }
            catch (IOException e)
            {
                return 0;
            }
        }

        @Override
        public String getName()
        {
            return name;
        }

    }
}
