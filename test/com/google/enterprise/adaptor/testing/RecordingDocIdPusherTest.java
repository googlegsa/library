// Copyright 2017 Google Inc. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.enterprise.adaptor.testing;

import static com.google.enterprise.adaptor.DocIdPusher.EVERYTHING_CASE_INSENSITIVE;
import static com.google.enterprise.adaptor.DocIdPusher.EVERYTHING_CASE_SENSITIVE;
import static com.google.enterprise.adaptor.DocIdPusher.FeedType.FULL;
import static com.google.enterprise.adaptor.DocIdPusher.FeedType.INCREMENTAL;
import static com.google.enterprise.adaptor.DocIdPusher.Record;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.enterprise.adaptor.Acl;
import com.google.enterprise.adaptor.DocId;
import com.google.enterprise.adaptor.GroupPrincipal;
import com.google.enterprise.adaptor.Principal;
import com.google.enterprise.adaptor.UserPrincipal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link RecordingDocIdPusher}.
 */
public class RecordingDocIdPusherTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private RecordingDocIdPusher pusher = new RecordingDocIdPusher();

  @Test
  public void testPushDocIds() throws Exception {
    List<DocId> docids12 = ImmutableList.of(new DocId("1"), new DocId("2"));
    List<DocId> docids23 = ImmutableList.of(new DocId("2"), new DocId("3"));

    assertTrue(pusher.getDocIds().toString(), pusher.getDocIds().isEmpty());
    pusher.pushDocIds(docids12);
    List<DocId> expected = Lists.newArrayList(docids12);
    assertEquals(expected, pusher.getDocIds());

    pusher.pushDocIds(docids23);
    expected.addAll(docids23);
    assertEquals(expected, pusher.getDocIds());

    pusher.reset();
    assertTrue(pusher.getDocIds().toString(), pusher.getDocIds().isEmpty());
  }

  @Test
  public void testPushRecords() throws Exception {
    List<Record> records12 = ImmutableList.of(
        new Record.Builder(new DocId("1")).setCrawlImmediately(true).build(),
        new Record.Builder(new DocId("2")).setCrawlImmediately(true).build());
    List<Record> records34 = ImmutableList.of(
        new Record.Builder(new DocId("3")).setCrawlImmediately(true).build(),
        new Record.Builder(new DocId("4")).setCrawlImmediately(true).build());

    assertTrue(pusher.getRecords().toString(), pusher.getRecords().isEmpty());
    pusher.pushRecords(records12);
    List<Record> expected = Lists.newArrayList(records12);
    assertEquals(expected, pusher.getRecords());

    pusher.pushRecords(records34);
    expected.addAll(records34);
    assertEquals(expected, pusher.getRecords());

    pusher.reset();
    assertTrue(pusher.getRecords().toString(), pusher.getRecords().isEmpty());
  }

  @Test
  public void testPushNamedResources() throws Exception {
    Map<DocId, Acl> acls12 = ImmutableMap.of(
        new DocId("1"), Acl.EMPTY,
        new DocId("2"), new Acl.Builder()
            .setPermits(ImmutableSet.<Principal>of(new UserPrincipal("user2")))
            .build());
    Map<DocId, Acl> acls23 = ImmutableMap.of(
        new DocId("2"), Acl.EMPTY,
        new DocId("3"), new Acl.Builder()
            .setPermits(ImmutableSet.<Principal>of(new UserPrincipal("user3")))
            .build());

    assertTrue(pusher.getNamedResources().toString(),
        pusher.getNamedResources().isEmpty());
    pusher.pushNamedResources(acls12);
    Map<DocId, Acl> expected = Maps.newHashMap(acls12);
    assertEquals(expected, pusher.getNamedResources());

    pusher.pushNamedResources(acls23);
    expected.putAll(acls23);
    assertEquals(expected, pusher.getNamedResources());

    pusher.reset();
    assertTrue(pusher.getNamedResources().toString(),
        pusher.getNamedResources().isEmpty());
  }

  @Test
  public void testGetGroupDefinitions_defaultSourceNonePushed() {
    assertTrue(pusher.getGroupDefinitions().toString(),
        pusher.getGroupDefinitions().isEmpty());
  }

  @Test
  public void testGetGroupDefinitions_customSourceNonePushed() {
    assertTrue(pusher.getGroupDefinitions("foo").toString(),
        pusher.getGroupDefinitions("foo").isEmpty());
  }

  @Test
  public void testPushGroupDefinitions_defaultSource() throws Exception {
    Map<GroupPrincipal, ? extends Set<Principal>> groups12 = ImmutableMap.of(
        new GroupPrincipal("group1"),
            ImmutableSet.<Principal>of(new UserPrincipal("user1")),
        new GroupPrincipal("group2"),
            ImmutableSet.<Principal>of(new UserPrincipal("user2")));
    Map<GroupPrincipal, ? extends Set<Principal>> groups23 = ImmutableMap.of(
        new GroupPrincipal("group2"),
            ImmutableSet.<Principal>of(new UserPrincipal("user200")),
        new GroupPrincipal("group3"),
            ImmutableSet.<Principal>of(new UserPrincipal("user3")));

    pusher.pushGroupDefinitions(groups12, EVERYTHING_CASE_INSENSITIVE);
    Map<GroupPrincipal, Set<Principal>> expected = Maps.newHashMap(groups12);
    assertEquals(expected, pusher.getGroupDefinitions());

    pusher.pushGroupDefinitions(groups23, EVERYTHING_CASE_INSENSITIVE);
    expected.putAll(groups23);
    assertEquals(expected, pusher.getGroupDefinitions());

    pusher.reset();
    assertTrue(pusher.getGroupDefinitions().toString(),
        pusher.getGroupDefinitions().isEmpty());
  }

  @Test
  public void testPushGroupDefinitions_defaultSourceFullFullIncremental()
      throws Exception {
    testPushGroupDefinitions_fullFullIncremental(null);
  }

  @Test
  public void testPushGroupDefinitions_singleSourceFullFullIncremental()
      throws Exception {
    testPushGroupDefinitions_fullFullIncremental("groupSource");
  }

  private void testPushGroupDefinitions_fullFullIncremental(String source)
      throws Exception {
    Map<GroupPrincipal, ? extends Set<Principal>> groups123 = ImmutableMap.of(
        new GroupPrincipal("group1"),
            ImmutableSet.<Principal>of(new UserPrincipal("user1")),
        new GroupPrincipal("group2"),
            ImmutableSet.<Principal>of(new UserPrincipal("user2")),
        new GroupPrincipal("group3"),
            ImmutableSet.<Principal>of(new UserPrincipal("user3")));
    Map<GroupPrincipal, ? extends Set<Principal>> groups456 = ImmutableMap.of(
        new GroupPrincipal("group4"),
            ImmutableSet.<Principal>of(new UserPrincipal("user4")),
        new GroupPrincipal("group5"),
            ImmutableSet.<Principal>of(new UserPrincipal("user5")),
        new GroupPrincipal("group6"),
            ImmutableSet.<Principal>of(new UserPrincipal("user6")));
    Map<GroupPrincipal, ? extends Set<Principal>> groups5 = ImmutableMap.of(
        new GroupPrincipal("group5"),
            ImmutableSet.<Principal>of(new UserPrincipal("user500")));

    pusher.pushGroupDefinitions(groups123, EVERYTHING_CASE_SENSITIVE, FULL,
        source, null);
    Map<GroupPrincipal, Set<Principal>> expected = Maps.newHashMap(groups123);
    assertEquals(expected, pusher.getGroupDefinitions());

    pusher.pushGroupDefinitions(groups456, EVERYTHING_CASE_SENSITIVE, FULL,
        source, null);
    expected = Maps.newHashMap(groups456);
    assertEquals(expected, pusher.getGroupDefinitions());

    pusher.pushGroupDefinitions(groups5, EVERYTHING_CASE_SENSITIVE,
        INCREMENTAL, source, null);
    expected.putAll(groups5);
    assertEquals(expected, pusher.getGroupDefinitions());

    pusher.reset();
    assertTrue(pusher.getGroupDefinitions().toString(),
        pusher.getGroupDefinitions().isEmpty());
  }

  @Test
  public void testPushGroupDefinitions_multipleSources() throws Exception {
    Map<GroupPrincipal, ? extends Set<Principal>> groups123 = ImmutableMap.of(
        new GroupPrincipal("group1"),
            ImmutableSet.<Principal>of(new UserPrincipal("user1")),
        new GroupPrincipal("group2"),
            ImmutableSet.<Principal>of(new UserPrincipal("user2")),
        new GroupPrincipal("group3"),
            ImmutableSet.<Principal>of(new UserPrincipal("user3")));
    Map<GroupPrincipal, ? extends Set<Principal>> groups456 = ImmutableMap.of(
        new GroupPrincipal("group4"),
            ImmutableSet.<Principal>of(new UserPrincipal("user4")),
        new GroupPrincipal("group5"),
            ImmutableSet.<Principal>of(new UserPrincipal("user5")),
        new GroupPrincipal("group6"),
            ImmutableSet.<Principal>of(new UserPrincipal("user6")));

    pusher.pushGroupDefinitions(groups123, EVERYTHING_CASE_SENSITIVE, FULL,
        "foo", null);
    pusher.pushGroupDefinitions(groups456, EVERYTHING_CASE_SENSITIVE, FULL,
        "bar", null);
    assertEquals(groups123, pusher.getGroupDefinitions("foo"));
    assertEquals(groups456, pusher.getGroupDefinitions("bar"));

    pusher.reset();
    assertTrue(pusher.getGroupDefinitions().toString(),
        pusher.getGroupDefinitions().isEmpty());
  }

  @Test
  public void testGetGroupDefinitions_oneArgSingleSource() throws Exception {
    Map<GroupPrincipal, ? extends List<Principal>> groups1 = ImmutableMap.of(
        new GroupPrincipal("group1"),
            ImmutableList.<Principal>of(new UserPrincipal("user1")));

    pusher.pushGroupDefinitions(groups1, EVERYTHING_CASE_SENSITIVE);
    assertEquals(groups1, pusher.getGroupDefinitions(null));
  }

  @Test
  public void testGetGroupDefinitions_noArgMultipleSources() throws Exception {
    Map<GroupPrincipal, ? extends Set<Principal>> groups1 = ImmutableMap.of(
        new GroupPrincipal("group1"),
            ImmutableSet.<Principal>of(new UserPrincipal("user1")));
    Map<GroupPrincipal, ? extends Set<Principal>> groups2 = ImmutableMap.of(
        new GroupPrincipal("group2"),
            ImmutableSet.<Principal>of(new UserPrincipal("user2")));

    pusher.pushGroupDefinitions(groups1, EVERYTHING_CASE_SENSITIVE, FULL, "foo",
        null);
    pusher.pushGroupDefinitions(groups2, EVERYTHING_CASE_SENSITIVE, FULL, "bar",
        null);

    thrown.expect(IllegalStateException.class);
    pusher.getGroupDefinitions();
  }
}
