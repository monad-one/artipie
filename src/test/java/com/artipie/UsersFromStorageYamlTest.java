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
package com.artipie;

import com.artipie.asto.Content;
import com.artipie.asto.Key;
import com.artipie.asto.Storage;
import com.artipie.asto.ext.PublisherAs;
import com.artipie.asto.memory.InMemoryStorage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.cactoos.list.ListOf;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test for {@link Users.FromStorageYaml}.
 * @since 0.9
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
@SuppressWarnings({"unchecked", "PMD.AvoidDuplicateLiterals"})
class UsersFromStorageYamlTest {

    /**
     * Test storage.
     */
    private Storage storage;

    /**
     * Test key.
     */
    private Key key;

    @BeforeEach
    void init() {
        this.storage = new InMemoryStorage();
        this.key = new Key.From("_cred.yaml");
    }

    @Test
    void readsYamlWithEmailFromStorage() {
        final Users.User jane = new Users.User(
            "jane", this.email("jane"), new ListOf<String>("readers")
        );
        final Users.User john = new Users.User(
            "john", this.email("john"), new ListOf<String>("reviewers", "supporters")
        );
        final Users.PasswordFormat sha = Users.PasswordFormat.SHA256;
        final String pass = "111";
        this.creds(sha, new ImmutablePair<>(jane, pass), new ImmutablePair<>(john, pass));
        MatcherAssert.assertThat(
            new Users.FromStorageYaml(this.storage, this.key).list()
                .toCompletableFuture().join(),
            Matchers.containsInAnyOrder(jane, john)
        );
    }

    @Test
    void readsYamlFromStorage() {
        final Users.User jane = new Users.User("maria");
        final Users.User john = new Users.User("olga");
        new CredsConfigYaml().withUsers(jane.name(), john.name()).saveTo(this.storage, this.key);
        MatcherAssert.assertThat(
            new Users.FromStorageYaml(this.storage, this.key).list()
                .toCompletableFuture().join(),
            Matchers.containsInAnyOrder(jane, john)
        );
    }

    @Test
    void addsUser() {
        final Users.User maria = new Users.User(
            "maria", this.email("maria"), new ListOf<>("newbies", "tester")
        );
        final Users.User olga = new Users.User(
            "olga", this.email("olga"), new ListOf<>("readers", "a-team")
        );
        final String pass = "abc";
        final Users.PasswordFormat sha = Users.PasswordFormat.SHA256;
        this.creds(sha, new ImmutablePair<>(maria, pass));
        new Users.FromStorageYaml(this.storage, this.key)
            .add(olga, DigestUtils.sha256Hex(pass), sha).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.storage.value(this.key).join())
                .asciiString().toCompletableFuture().join(),
            new IsEqual<>(
                this.getYamlWithEmailAndGroups(
                    sha,
                    new ImmutablePair<>(maria, pass),
                    new ImmutablePair<>(olga, pass)
                )
            )
        );
    }

    @Test
    void updatesUser() {
        final Users.User jack = new Users.User("jack");
        final Users.User silvia = new Users.User(
            "silvia", this.email("silvia"), new ListOf<>("readers")
        );
        final String old = "345";
        final String newpass = "000";
        final Users.PasswordFormat plain = Users.PasswordFormat.PLAIN;
        this.creds(plain, new ImmutablePair<>(jack, old), new ImmutablePair<>(silvia, old));
        new Users.FromStorageYaml(this.storage, this.key)
            .add(silvia, newpass, Users.PasswordFormat.PLAIN).toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.storage.value(this.key).join())
                .asciiString().toCompletableFuture().join(),
            new IsEqual<>(
                this.getYamlWithEmailAndGroups(
                    plain,
                    new ImmutablePair<>(jack, old),
                    new ImmutablePair<>(silvia, newpass)
                )
            )
        );
    }

    @Test
    void removesUser() {
        final Users.User mark = new Users.User("mark");
        final Users.User ann = new Users.User("ann");
        final String pass = "123";
        final Users.PasswordFormat plain = Users.PasswordFormat.PLAIN;
        this.creds(plain, new ImmutablePair<>(mark, pass), new ImmutablePair<>(ann, pass));
        new Users.FromStorageYaml(this.storage, this.key).remove(ann.name())
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.storage.value(this.key).join())
                .asciiString().toCompletableFuture().join(),
            new IsEqual<>(this.getYamlWithEmailAndGroups(plain, new ImmutablePair<>(mark, pass)))
        );
    }

    @Test
    void doNotChangeYamlOnRemoveIfUserNotFound() {
        final Users.User ted = new Users.User("ted");
        final Users.User alex = new Users.User("alex");
        final String pass = "098";
        final Users.PasswordFormat plain = Users.PasswordFormat.PLAIN;
        this.creds(plain, new ImmutablePair<>(ted, pass), new ImmutablePair<>(alex, pass));
        new Users.FromStorageYaml(this.storage, this.key).remove("alice")
            .toCompletableFuture().join();
        MatcherAssert.assertThat(
            new PublisherAs(this.storage.value(this.key).join())
                .asciiString().toCompletableFuture().join(),
            new IsEqual<>(
                this.getYamlWithEmailAndGroups(
                    plain,
                    new ImmutablePair<>(ted, pass), new ImmutablePair<>(alex, pass)
                )
            )
        );
    }

    private void creds(final Users.PasswordFormat format, final Pair<Users.User, String>... users) {
        this.storage.save(
            this.key,
            new Content.From(
                this.getYamlWithEmailAndGroups(format, users).getBytes(StandardCharsets.UTF_8)
            )
        ).join();
    }

    private String getYamlWithEmailAndGroups(final Users.PasswordFormat format,
        final Pair<Users.User, String>... users) {
        final CredsConfigYaml creds = new CredsConfigYaml();
        for (final Pair<Users.User, String> user : users) {
            creds.withFullInfo(
                user.getKey().name(), format,
                user.getValue(), this.email(user.getKey().name()).get(), user.getKey().groups()
            );
        }
        return creds.toString();
    }

    private Optional<String> email(final String name) {
        return Optional.of(String.format("%s@example.com", name));
    }

}
