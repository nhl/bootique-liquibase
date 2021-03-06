/*
 * Licensed to ObjectStyle LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ObjectStyle LLC licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.bootique.liquibase;

import io.bootique.BQRuntime;
import io.bootique.command.CommandOutcome;
import io.bootique.di.BQModule;
import io.bootique.jdbc.junit5.Table;
import io.bootique.jdbc.junit5.derby.DerbyTester;
import io.bootique.jdbc.liquibase.LiquibaseRunner;
import io.bootique.junit5.BQTest;
import io.bootique.junit5.BQTestFactory;
import io.bootique.junit5.BQTestTool;
import io.bootique.junit5.TestRuntumeBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@BQTest
public class UpdateCommand_DerbyIT {

    @BQTestTool
    final DerbyTester db = DerbyTester.db();

    @BQTestTool
    final BQTestFactory testFactory = new BQTestFactory();

    private CommandOutcome run(DerbyTester db, String[] cli, BQModule... extras) {
        TestRuntumeBuilder builder = testFactory
                .app(cli)
                .autoLoadModules()
                .module(db.moduleWithTestDataSource("test"));

        for (BQModule e : extras) {
            builder.module(e);
        }

        return builder.run();
    }

    @Test
    public void testSingleSet() {

        String[] cli = {"-c", "classpath:io/bootique/liquibase/migrations1.yml", "-u", "-d", "target/derby/migrations1"};
        CommandOutcome migrate1 = run(db, cli);

        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertOneMatch();
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertOneMatch();
    }

    @Test
    public void testMultipleSets() {

        String[] cli = {"-c", "classpath:io/bootique/liquibase/migrations2.yml", "-u"};

        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(2);
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();
        a.matcher().eq("ID", 2).eq("NAME", "BB").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        db.getTable("A").matcher().assertMatches(2);
    }

    @Test
    public void testMultipleSetsViaYaml() {
        String[] cli = {"-c", "classpath:io/bootique/liquibase/migrations3.yml", "-u"};

        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(2);
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();
        a.matcher().eq("ID", 2).eq("NAME", "BB").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(2);
    }

    @Test
    public void testMultipleSetsContribution() {
        String[] cli = {"-u"};
        CommandOutcome migrate1 = run(db, cli, b -> LiquibaseModule.extend(b)
                .addChangeLog("classpath:io/bootique/liquibase/changeset1.sql")
                .addChangeLog("classpath:io/bootique/liquibase/changeset2.sql"));

        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(2);
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();
        a.matcher().eq("ID", 2).eq("NAME", "BB").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(2);
    }

    @Test
    public void testYamlOverridesDI() {

        String[] cli = {"-c", "classpath:io/bootique/liquibase/migrations3.yml", "-u"};
        CommandOutcome migrate1 = run(db, cli, b -> LiquibaseModule.extend(b)
                .addChangeLog("classpath:io/bootique/liquibase/changeset1.sql")
                .addChangeLog("classpath:io/bootique/liquibase/changeset2.sql")
                .addChangeLog("classpath:io/bootique/liquibase/changeset3.sql"));

        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(2);
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();
        a.matcher().eq("ID", 2).eq("NAME", "BB").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(2);
    }

    @Test
    public void testDefaultDataSource() {

        String[] cli = {"-c", "classpath:io/bootique/liquibase/migrations1_implicit_ds.yml", "-u"};
        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertOneMatch();
        a.matcher().eq("ID", 1).eq("NAME", "AA").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertOneMatch();
    }

    @Test
    public void testContext() {
        String[] cli = {"-c", "classpath:io/bootique/liquibase/migration_context.yml", "-u", "-x", "test", "-x", "prod"};
        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(5);
        a.matcher().eq("ID", 1).eq("CONTEXT", "prod").assertOneMatch();
        a.matcher().eq("ID", 2).eq("CONTEXT", "test").assertOneMatch();
        a.matcher().eq("ID", 3).eq("CONTEXT", "no context").assertOneMatch();
        a.matcher().eq("ID", 4).eq("CONTEXT", "test and prod").assertOneMatch();
        a.matcher().eq("ID", 5).eq("CONTEXT", "test or prod").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(5);
    }

    @Test
    public void testNoExplicitContext() {

        // no explicit context, so all contexts must be run

        String[] cli = {"-c", "classpath:io/bootique/liquibase/migration_context.yml", "-u"};
        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(7);

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(7);
    }

    @Test
    public void testUnknownContext() {
        String[] cli = {"-c", "classpath:io/bootique/liquibase/migration_context.yml", "-u", "-x", "unknown"};
        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table a = db.getTable("A");
        a.matcher().assertMatches(3);
        a.matcher().eq("ID", 3).eq("CONTEXT", "no context").assertOneMatch();
        a.matcher().eq("ID", 6).eq("CONTEXT", "!test").assertOneMatch();
        a.matcher().eq("ID", 7).eq("CONTEXT", "!test and !prod").assertOneMatch();

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        a.matcher().assertMatches(3);
    }

    @Test
    public void testSchema() {
        String[] cli = {"-c", "classpath:io/bootique/liquibase/migration_schema.yml", "-u"};
        CommandOutcome migrate1 = run(db, cli);
        assertTrue(migrate1.isSuccess());

        Table y = db.getTable("Y");
        y.matcher().assertOneMatch();
        y.matcher().eq("ID", 1).eq("SCHEMA", "APP").assertOneMatch();

        Table x = db.getTable("X");
        assertThrows(RuntimeException.class, () -> x.selectAllColumns().select());

        // rerun....
        CommandOutcome migrate2 = run(db, cli);
        assertTrue(migrate2.isSuccess());
        y.matcher().assertOneMatch();
    }

    @Test
    public void testDefaultSchema() {
        BQRuntime runtime = testFactory
                .app("-c", "classpath:io/bootique/liquibase/migration_schema.yml", "-u", "-d", "test")
                .module(db.moduleWithTestDataSource("test"))
                .autoLoadModules()
                .createRuntime();

        LiquibaseRunner runner = runtime.getInstance(LiquibaseRunner.class);
        runner.run(lb -> {
            assertEquals("TEST", lb.getDatabase().getDefaultSchemaName());
            assertEquals("TEST", lb.getDatabase().getLiquibaseSchemaName());
        });
    }
}
