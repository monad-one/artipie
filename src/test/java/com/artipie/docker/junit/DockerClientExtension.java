/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020 artipie.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.artipie.docker.junit;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Docker client extension. Populates {@link DockerClient} field of test class.
 *
 * @since 0.10
 */
public final class DockerClientExtension
    implements BeforeEachCallback, BeforeAllCallback, AfterAllCallback {

    /**
     * Key for storing client instance in context store.
     */
    private static final String CLIENT = "client";

    /**
     * Key for storing temp dir in context store.
     */
    private static final String TEMP_DIR = "temp-dir";

    @Override
    public void beforeAll(final ExtensionContext context) throws Exception {
        final Path temp = Files.createTempDirectory("junit-docker-");
        final DockerClient client = new DockerClient(temp);
        store(context).put(DockerClientExtension.TEMP_DIR, temp);
        store(context).put(DockerClientExtension.CLIENT, client);
    }

    @Override
    public void beforeEach(final ExtensionContext context) throws Exception {
        injectVariables(
            context,
            store(context).get(DockerClientExtension.CLIENT, DockerClient.class)
        );
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        final Path temp = store(context).remove(DockerClientExtension.TEMP_DIR, Path.class);
        temp.toFile().delete();
        store(context).remove(DockerClientExtension.CLIENT);
    }

    /**
     * Injects {@link DockerClient} variables in the test instance.
     *
     * @param context JUnit extension context
     * @param client Docker client instance
     * @throws Exception When something get wrong
     */
    private static void injectVariables(final ExtensionContext context, final DockerClient client)
        throws Exception {
        final Object instance = context.getRequiredTestInstance();
        for (final Field field : context.getRequiredTestClass().getDeclaredFields()) {
            if (field.getType().isAssignableFrom(DockerClient.class)) {
                ensureFieldIsAccessible(instance, field);
                field.set(instance, client);
            }
        }
    }

    /**
     * Try to set field accessible.
     *
     * @param instance Object instance
     * @param field Class field that need to be accessible
     */
    private static void ensureFieldIsAccessible(final Object instance, final Field field) {
        if (!field.canAccess(instance)) {
            field.setAccessible(true);
        }
    }

    /**
     * Get store from context.
     *
     * @param context JUnit extension context.
     * @return Store.
     */
    private static ExtensionContext.Store store(final ExtensionContext context) {
        return context.getStore(ExtensionContext.Namespace.create(DockerClientExtension.class));
    }
}
