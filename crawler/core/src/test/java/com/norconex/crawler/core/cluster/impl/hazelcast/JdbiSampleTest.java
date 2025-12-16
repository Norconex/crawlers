package com.norconex.crawler.core.cluster.impl.hazelcast;

class Person {
    private long id;
    private String name;

    // getters and setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

public class JdbiSampleTest {
    //    private DataSource createH2DataSource() {
    //        HikariConfig config = new HikariConfig();
    //        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    //        config.setUsername("sa");
    //        config.setPassword("");
    //        return new HikariDataSource(config);
    //    }
    //
    //    @Test
    //    void testJdbiSimpleCrud() {
    //        DataSource ds = createH2DataSource();
    //        Jdbi jdbi = Jdbi.create(ds);
    //        try (Handle handle = jdbi.open()) {
    //            handle.execute("CREATE TABLE person (id BIGINT PRIMARY KEY, name VARCHAR(100))");
    //            handle.execute("INSERT INTO person (id, name) VALUES (?, ?)", 1L, "Alice");
    //            handle.execute("INSERT INTO person (id, name) VALUES (?, ?)", 2L, "Bob");
    //        }
    //        List<Person> people = jdbi.withHandle(h ->
    //            h.createQuery("SELECT * FROM person ORDER BY id")
    //             .registerRowMapper(BeanMapper.factory(Person.class))
    //             .mapTo(Person.class)
    //             .list()
    //        );
    //        Assertions.assertThat(people).hasSize(2);
    //        Assertions.assertThat(people.get(0).getName()).isEqualTo("Alice");
    //        Assertions.assertThat(people.get(1).getName()).isEqualTo("Bob");
    //    }
}
